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

def build_backend():
    # 必须 clean：增量编译会复用过期 target/classes，把不是当前源码的产物打进 jar
    print('[构建] yudao-cloud: mvn -B -DskipTests clean package（全量，约 6 分钟）')
    subprocess.run('mvn -B -DskipTests clean package',
                   cwd=ROOT / 'yudao-cloud', shell=True, check=True)


def deploy_backend(*, skip_build, dry_run):
    if not skip_build and not dry_run:
        build_backend()

    if not LOCAL_JAR.is_file():
        sys.exit(f'找不到 {LOCAL_JAR} —— 先打包，或去掉 --dry-run')

    mb = LOCAL_JAR.stat().st_size / 1024 / 1024
    print(f'[产物] {LOCAL_JAR.name} {mb:.1f} MB')
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
