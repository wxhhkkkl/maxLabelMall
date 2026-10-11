"""把 storefront / yudao-server 发到线上（阿里云应用机 8.146.233.197）。

用法
----
    python scripts/deploy.py frontend              # 构建 + 上传静态站点（最常用）
    python scripts/deploy.py backend               # 打包 jar + 上传 + 重启 + 等起来
    python scripts/deploy.py all                   # 先前端后后端
    python scripts/deploy.py frontend --skip-build # 复用已有 dist/，只上传
    python scripts/deploy.py frontend --dry-run    # 只列会传/会跳过哪些，不落盘

线上形态（摸清于 2026-10-10，别再重新探索一遍）
------------------------------------------------
    nginx 站点  /var/www/maxlabel   ← index.html + assets/（storefront）
                                    ← admin/ 是**另一套构建**，本脚本绝不碰
    systemd     maxlabel.service    → java -jar /opt/maxlabel/app/yudao-server.jar
    应用日志    /opt/maxlabel/logs/app.log
    外部配置    /opt/maxlabel/app/application-my.yaml（优先级高于 jar 内那份）

两条硬规矩
----------
1. **只增不删。** `assets/` 里都是带内容哈希的产物名，旧文件是孤儿但必须留着 ——
   还停在旧 `index.html` 上的会话拉分片时全靠它们。远程多出来的文件一概不碰。
2. **`index.html` 无条件重传。** 它没有哈希名，而新旧两版的字节数**可能完全一样**
   （2026-10-10 实测：两版都是 575 字节，只差入口 hash 那几个字符）。当时用
   「同名同大小就跳过」把它静默跳过，表现是**资产全换了、首页还是旧的**，
   脚本却一路打印"成功"。这条已经在 `test_deploy.py` 里被钉死。

凭据取仓库根的 `.env`（`SERVER_IP` / `SERVER_USER` / `SERVER_PASSWRD`），
与 `_dbtunnel.py` 同一套。
"""
import argparse
import io
import re
import subprocess
import sys
import time
from pathlib import Path

import paramiko

# 控制台编码兜底：下面用了 ✓ ✗ ↑ 这些 GBK 编不出的字符，Windows 默认按 GBK 输出
# 会直接 UnicodeEncodeError —— **崩在发版中途**比乱码难受得多，所以先兜住。
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding='utf-8', errors='replace')
    except (AttributeError, ValueError):
        pass

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / 'storefront' / 'dist'
LOCAL_JAR = ROOT / 'yudao-cloud' / 'yudao-server' / 'target' / 'yudao-server.jar'

REMOTE_WWW = '/var/www/maxlabel'
REMOTE_APP = '/opt/maxlabel/app'
SERVICE = 'maxlabel'
BACKEND_PORT = 48080
# nginx 上的 default_server 凭 Host 头区分站点，所以源站自检要带上它
PUBLIC_HOST = 'new.yuwangchenfa.com'
BACKEND_BOOT_WAIT = 120  # 实测约 73 秒起来，留足余量


# ────────────────────────────── 纯函数区（见 test_deploy.py） ──────────────────────────────

def plan_uploads(files, remote_size):
    """算出该传哪些、可跳过哪些。

    :param files: [(相对路径, 本地字节数)]，顺序即上传顺序
    :param remote_size: 回调，传相对路径，返回远程字节数；远程没有则返回 None
    :return: (要传的路径列表, 可跳过的路径列表)

    规则：`index.html` **永远传**（理由见模块头注释第 2 条）；其余都是内容哈希命名的
    产物，同名必然同内容，同名同大小即可安全跳过 —— 省的是上传时间。
    """
    send, skip = [], []
    for rel, size in files:
        if rel != 'index.html' and remote_size(rel) == size:
            skip.append(rel)
        else:
            send.append(rel)
    return send, skip


def local_entries(dist_dir):
    """列出 dist 下所有文件 → [(相对路径, 字节数)]，按路径排序保证顺序稳定。"""
    return sorted(
        ((p.relative_to(dist_dir).as_posix(), p.stat().st_size)
         for p in dist_dir.rglob('*') if p.is_file()),
        key=lambda t: t[0],
    )


def entry_of(index_html: str):
    """从 index.html 里抠出入口 chunk 的名字，用来核对线上跑的是哪一版。"""
    m = re.search(r'assets/index-[A-Za-z0-9_.-]+\.js', index_html)
    return m.group(0) if m else None


# ─────────────────────────────────── 远程执行 ───────────────────────────────────

def load_env():
    env = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
    for key in ('SERVER_IP', 'SERVER_USER', 'SERVER_PASSWRD'):
        if not env.get(key):
            sys.exit(f'.env 里缺 {key}')
    return env


class Server:
    def __init__(self):
        env = load_env()
        self._cli = paramiko.SSHClient()
        self._cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self._cli.connect(env['SERVER_IP'], username=env['SERVER_USER'],
                          password=env['SERVER_PASSWRD'], timeout=30)
        self.sftp = self._cli.open_sftp()

    def close(self):
        try:
            self.sftp.close()
        finally:
            self._cli.close()

    def __enter__(self):
        return self

    def __exit__(self, *_exc):
        self.close()

    def run(self, cmd, timeout=60):
        _, out, err = self._cli.exec_command(cmd, timeout=timeout)
        return (out.read().decode('utf-8', 'replace').strip(),
                err.read().decode('utf-8', 'replace').strip())

    def size(self, remote):
        try:
            return self.sftp.stat(remote).st_size
        except IOError:
            return None

    def read(self, remote):
        with self.sftp.open(remote) as f:
            return f.read().decode('utf-8', 'replace')

    def mkdirs(self, remote_dir):
        self.run(f'mkdir -p {remote_dir}')

    def put(self, local, remote, on_progress=None):
        self.sftp.put(str(local), remote, callback=on_progress)


# ───────────────────────────────────── 前端 ─────────────────────────────────────

def build_frontend():
    print('[构建] storefront: pnpm build（含 vue-tsc 类型检查）')
    subprocess.run('pnpm build', cwd=ROOT / 'storefront', shell=True, check=True)


def deploy_frontend(*, skip_build, dry_run):
    if not skip_build and not dry_run:
        build_frontend()

    if not (DIST / 'index.html').is_file():
        sys.exit(f'找不到 {DIST}/index.html —— 先构建，或去掉 --dry-run')

    with Server() as srv:
        files = local_entries(DIST)
        send, skip = plan_uploads(files, lambda rel: srv.size(f'{REMOTE_WWW}/{rel}'))
        print(f'[比对] 本地 {len(files)} 个文件：需上传 {len(send)}，可跳过 {len(skip)}')
        for rel in send:
            print(f'         ↑ {rel}')

        if dry_run:
            print('[dry-run] 到此为止，什么都没传。')
            return

        stamp = time.strftime('%Y%m%d-%H%M%S')
        bak = f'{REMOTE_WWW}/index.html.bak-{stamp}'
        out, err = srv.run(f'cp -p {REMOTE_WWW}/index.html {bak} && echo ok')
        if out != 'ok':
            sys.exit(f'备份 index.html 失败：{err or out}')
        print(f'[备份] {bak}（回滚 = 把它拷回 index.html）')

        sent = 0
        for rel in send:
            remote = f'{REMOTE_WWW}/{rel}'
            srv.mkdirs(remote.rsplit('/', 1)[0])
            srv.put(DIST / rel, remote)
            sent += 1
        print(f'[上传] 完成 {sent} 个文件')

        verify_frontend(srv)


def verify_frontend(srv):
    """核对**源站**（不经 CDN），因为 CDN 会把归因搅浑：源站没更新时，
    公网看到的旧页面既可能是 CDN 缓存、也可能是真没传上去。"""
    local_entry = entry_of((DIST / 'index.html').read_text(encoding='utf-8'))
    remote_entry = entry_of(srv.read(f'{REMOTE_WWW}/index.html'))
    if local_entry != remote_entry:
        sys.exit(f'[✗] 源站 index.html 还是旧的：本地 {local_entry} / 远程 {remote_entry}')

    out, _ = srv.run(f"curl -s -H 'Host: {PUBLIC_HOST}' http://127.0.0.1/ "
                     f"| grep -o 'assets/index-[A-Za-z0-9_.-]*\\.js' | head -1")
    if out != local_entry:
        sys.exit(f'[✗] 源站 nginx 吐出来的入口是 {out or "(空)"}，期望 {local_entry}')

    print(f'[✓] 前端已更新，源站入口 = {local_entry}')


# ───────────────────────────────────── 后端 ─────────────────────────────────────

def remove_polluted_classes(hits):
    """把 jar 里那些被 IDE 编坏的 class，从对应的 `target/classes` 里删掉。

    删掉之后，增量编译会发现"类文件缺失"并把它们**从源码重新生成**（这次是 javac 生成的、
    干净的）。这比"整个 clean 重来"快得多，而且**不会再触发 IDE 的又一次全量重编**。

    条目形如：
        BOOT-INF/classes/cn/.../Foo.class                        → yudao-server/target/classes/...
        BOOT-INF/lib/yudao-module-x-server-1.2.3.jar!cn/.../Foo.class
                                                                → 找到该模块的 target/classes/...
    """
    removed = 0
    for hit in hits:
        if '!' in hit:
            jar_entry, inner = hit.split('!', 1)
            # yudao-module-member-server-2026.08-jdk25-SNAPSHOT.jar → yudao-module-member-server
            artifact = jar_entry.rsplit('/', 1)[-1][:-len('.jar')]
            module = artifact.rsplit('-', 3)[0]  # 去掉 -2026.08-jdk25-SNAPSHOT 这段
            candidates = list((ROOT / 'yudao-cloud').rglob(f'{module}/target/classes/{inner}'))
        else:
            inner = hit[len('BOOT-INF/classes/'):]
            candidates = [ROOT / 'yudao-cloud' / 'yudao-server' / 'target' / 'classes' / inner]
        for path in candidates:
            if path.is_file():
                path.unlink()
                removed += 1
    return removed


def build_backend():
    """打包 jar —— **并且确保里面没有 IDE 编坏的 class**。

    ⚠️ 2026-10-11 生产事故：本机开着 VSCode 的 Java 语言服务器时，它按自己的 classpath
    把工作区编进各模块的 `target/classes`，其中包含带编译错误的 class（ECJ 会照样产出，
    只是运行到就抛 `Unresolved compilation problems`）。而 `mvn clean` 会把 target/classes
    整个删掉，**正好触发它全量重编** —— 于是每次构建都在跟它抢同一个目录。
    实测：坏 class 写在 11:42:10，jar 打好在 11:42:12，差 2 秒被卷进去。
    **maven 报 BUILD SUCCESS**，只在运行时炸（`NoClassDefFoundError: Could not initialize class`）。

    所以这里不能只跑一条 `mvn clean package` 就完事 —— 跑完必须**验**，脏了就就地修。
    彻底的办法是构建时关掉 IDE 的 Java 语言服务器；这个函数是不关也能用的兜底。
    """
    root = ROOT / 'yudao-cloud'
    print('[构建] yudao-cloud: mvn -B -DskipTests clean package（全量，约 6 分钟）')
    subprocess.run('mvn -B -DskipTests clean package', cwd=root, shell=True, check=True)

    for attempt in range(1, 4):
        hits = scan_jar_for_ecj(LOCAL_JAR)
        if not hits:
            if attempt > 1:
                print(f'[✓] 第 {attempt} 次打包后干净了')
            return
        print(f'[!] 第 {attempt} 次打包发现 {len(hits)} 个被 IDE 编坏的 class，就地修复后重新打包：')
        for h in hits[:5]:
            print(f'      {h}')
        removed = remove_polluted_classes(hits)
        if removed == 0:
            sys.exit('这些坏 class 在 target/classes 里找不到对应文件，无法就地修复 —— '
                     '请关掉 IDE 的 Java 语言服务器后重跑')
        # 增量打包：不 clean（勿再触发 IDE 全量重编），跳过测试编译（它会被坏 class 连累）
        subprocess.run('mvn -B -Dmaven.test.skip=true package',
                       cwd=root, shell=True, check=True)

    sys.exit(f'连续 3 次打包后 jar 里仍有 IDE 编坏的 class —— 请关掉 IDE 的 Java 语言服务器后重跑')


# ECJ（Eclipse 编译器）在"源码有编译错误"时仍会产出 class，只是把错误塞进
# 方法体里，运行到就抛 `java.lang.Error: Unresolved compilation problems`。
# 这个串就是它的指纹。
ECJ_MARKER = b'Unresolved compilation problems'


def scan_jar_for_ecj(jar_path):
    """扫 jar 里有没有被 **IDE（Eclipse/VSCode 的 Java 语言服务器）** 编坏掉的 class。

    为什么需要这个（2026-10-11 生产事故）：
        本机开着 VSCode 的 Java 语言服务器时，它会按自己的 classpath 把工作区编进各模块的
        `target/classes`。它的 classpath 与 maven 的不一致（比如看不到刚 install 的
        `yudao-module-product-api` 快照），于是**编出带错误的、能加载但一构造就抛错的 class**。
        实测时间戳：坏 class 写在 `11:42:10`，jar 打好在 `11:42:12` —— 差 2 秒，
        正好被 `maven-jar-plugin` 卷进去。javac 不会这样，所以**构建是 BUILD SUCCESS**，
        错误只在运行时才炸：`NoClassDefFoundError: Could not initialize class ...Convert`。

    只扫 `BOOT-INF/classes/` 与 `BOOT-INF/lib/yudao-*.jar`：第三方依赖不可能被 IDE 编译。
    返回命中的 class 名列表（空 = 干净）。
    """
    import zipfile

    def scan_bytes(data, prefix=''):
        return [prefix] if ECJ_MARKER in data else []

    hits = []
    with zipfile.ZipFile(jar_path) as jar:
        for name in jar.namelist():
            if name.startswith('BOOT-INF/classes/') and name.endswith('.class'):
                if ECJ_MARKER in jar.read(name):
                    hits.append(name)
            elif name.startswith('BOOT-INF/lib/yudao-') and name.endswith('.jar'):
                with zipfile.ZipFile(io.BytesIO(jar.read(name))) as nested:
                    for inner in nested.namelist():
                        if inner.endswith('.class') and ECJ_MARKER in nested.read(inner):
                            hits.append(f'{name}!{inner}')
    return hits


def deploy_backend(*, skip_build, dry_run):
    if not skip_build and not dry_run:
        build_backend()

    if not LOCAL_JAR.is_file():
        sys.exit(f'找不到 {LOCAL_JAR} —— 先打包，或去掉 --dry-run')

    mb = LOCAL_JAR.stat().st_size / 1024 / 1024
    print(f'[产物] {LOCAL_JAR.name} {mb:.1f} MB')

    # ⚠️ 上传前先验一遍：IDE 的语言服务器可能刚把它编坏的 class 覆盖进 target/classes，
    #    而那是 BUILD SUCCESS、看不出来的（见 scan_jar_for_ecj 的说明）。
    hits = scan_jar_for_ecj(LOCAL_JAR)
    if hits:
        print(f'[✗] jar 里有 {len(hits)} 个被 IDE 编坏的 class（含 "Unresolved compilation problems"）：')
        for h in hits[:10]:
            print(f'      {h}')
        sys.exit('拒绝发布这个 jar —— 重新跑一次构建（关掉 IDE 的 Java 语言服务器更稳），'
                 '或直接重试：这是它与 maven 的竞态，重跑一次通常就干净了')
    print('[✓] jar 里没有 IDE 编坏的 class')

    if dry_run:
        print('[dry-run] 到此为止，什么都没传、没重启。')
        return

    with Server() as srv:
        stamp = time.strftime('%Y%m%d-%H%M%S')
        staging = f'{REMOTE_APP}/yudao-server.jar.new'
        print(f'[上传] → {staging}（257 MB 实测约 3.5 分钟）')

        state = {'last': -1}

        def progress(done, total):
            pct = int(done * 100 / total)
            if pct >= state['last'] + 10:
                state['last'] = pct - pct % 10
                print(f'         {state["last"]}%')

        srv.put(LOCAL_JAR, staging, on_progress=progress)

        # 备份 → 原子替换 → 重启（先落 .new 再 mv，避免传一半就被进程读到）
        out, err = srv.run(
            f'cd {REMOTE_APP} && cp -p yudao-server.jar /root/yudao-server.jar.bak-{stamp} '
            f'&& mv {staging} yudao-server.jar && echo ok'
        )
        if out != 'ok':
            sys.exit(f'替换 jar 失败：{err or out}')
        print(f'[备份] /root/yudao-server.jar.bak-{stamp}')

        srv.run('systemctl restart ' + SERVICE)
        print(f'[重启] systemctl restart {SERVICE}，等待起来（约 73 秒）…')
        verify_backend(srv)


def verify_backend(srv):
    deadline = time.time() + BACKEND_BOOT_WAIT
    while time.time() < deadline:
        time.sleep(5)
        code, _ = srv.run(
            f"curl -s -o /dev/null -w '%{{http_code}}' --max-time 5 "
            f'http://127.0.0.1:{BACKEND_PORT}/'
        )
        # 连不上时 curl 给 000；只要不是 000 就说明 Tomcat 已在监听
        if code and code != '000':
            elapsed = int(BACKEND_BOOT_WAIT - (deadline - time.time()))
            print(f'[✓] 后端已监听 {BACKEND_PORT}（约 {elapsed} 秒），HTTP {code}')
            break
    else:
        sys.exit(f'[✗] {BACKEND_BOOT_WAIT} 秒内没起来，看 /opt/maxlabel/logs/app.log')

    active, _ = srv.run(f'systemctl is-active {SERVICE}')
    errors, _ = srv.run(
        f'journalctl -u {SERVICE} --since "-3min" | grep -ciE "ERROR|Exception"'
    )
    print(f'[✓] {SERVICE} is-active = {active}；近 3 分钟日志里 ERROR/Exception 计数 = {errors}')
    if active != 'active':
        sys.exit('[✗] 服务不是 active')


# ───────────────────────────────────── CLI ─────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(description='发版到线上应用机')
    ap.add_argument('target', choices=['frontend', 'backend', 'all'])
    ap.add_argument('--skip-build', action='store_true', help='复用已有产物，只上传')
    ap.add_argument('--dry-run', action='store_true', help='只列会传哪些，不落盘、不重启')
    args = ap.parse_args()

    if args.target in ('frontend', 'all'):
        deploy_frontend(skip_build=args.skip_build, dry_run=args.dry_run)
    if args.target in ('backend', 'all'):
        deploy_backend(skip_build=args.skip_build, dry_run=args.dry_run)


if __name__ == '__main__':
    main()
