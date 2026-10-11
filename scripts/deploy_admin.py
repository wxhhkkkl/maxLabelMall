"""把管理端（yudao-ui-admin-vue3）发到线上 `/var/www/maxlabel/admin/`。

为什么会有这个脚本
------------------
`scripts/deploy.py` 只管 storefront 与服务端 jar，模块头里写得很明白：
「admin/ 是**另一套构建**，本脚本绝不碰」。于是管理端此前**没有任何发版流程的记载** ——
2026-10-11 发特性 003 时才发现这个缺口（那一版改了三端，管理端也在内）。

本脚本**复用 `deploy.py` 里那些已经落地过的做法**（`plan_uploads` / `local_entries` /
`entry_of` / `Server`），而不是另写一套上传：它的两条硬规矩在这一端同样成立 ——
    1. **只增不删**：旧 `assets/` 是还停在旧 `index.html` 上的会话的分片来源
    2. **`index.html` 无条件重传**：它没有哈希名，新旧两版字节数可能一样，
       "同名同大小就跳过"会让资产全换了、首页还是旧的，而脚本一路打印"成功"

与 storefront 的差别只有两处：
    构建  `pnpm build:deploy`（走 `.env.deploy`：`VITE_BASE_PATH=/admin/`、
          `VITE_CAPTCHA_ENABLE=true`），不是 `pnpm build`
    目录  `/var/www/maxlabel/admin`，不是 `/var/www/maxlabel`

用法：
    python scripts/deploy_admin.py --dry-run   # 只看：会传什么、远程现在什么样
    python scripts/deploy_admin.py             # 构建 + 上传 + 核对源站入口
    python scripts/deploy_admin.py --skip-build
"""
import argparse
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from deploy import (  # noqa: E402  —— 复用同一套做法
    PUBLIC_HOST,
    ROOT,
    Server,
    entry_of,
    local_entries,
    plan_uploads,
)

ADMIN_DIR = ROOT / 'yudao-cloud' / 'yudao-ui' / 'yudao-ui-admin-vue3'
ADMIN_DIST = ADMIN_DIR / 'dist'
REMOTE_ADMIN = '/var/www/maxlabel/admin'
# 管理端的静态资源挂在 /admin/ 下，源站自检要打这个路径
ORIGIN_PATH = '/admin/'


def build_admin():
    print('[构建] 管理端: pnpm build:deploy（走 .env.deploy，base=/admin/）')
    subprocess.run('pnpm build:deploy', cwd=ADMIN_DIR, shell=True, check=True)


def verify(srv):
    """核对**源站**（不经 CDN）：CDN 会把归因搅浑 —— 源站没更新时，
    公网看到的旧页面既可能是 CDN 缓存、也可能是真没传上去。"""
    local_entry = entry_of((ADMIN_DIST / 'index.html').read_text(encoding='utf-8'))
    remote_entry = entry_of(srv.read(f'{REMOTE_ADMIN}/index.html'))
    if local_entry != remote_entry:
        sys.exit(f'[✗] 源站 index.html 还是旧的：本地 {local_entry} / 远程 {remote_entry}')

    out, _ = srv.run(f"curl -s -H 'Host: {PUBLIC_HOST}' http://127.0.0.1{ORIGIN_PATH} "
                     f"| grep -o 'assets/index-[A-Za-z0-9_.-]*\\.js' | head -1")
    if out != local_entry:
        sys.exit(f'[✗] 源站 nginx 在 {ORIGIN_PATH} 吐出来的入口是 {out or "(空)"}，期望 {local_entry}')

    print(f'[✓] 管理端已更新，源站入口 = {local_entry}')


def deploy(*, skip_build, dry_run):
    if not skip_build and not dry_run:
        build_admin()

    if not (ADMIN_DIST / 'index.html').is_file():
        sys.exit(f'找不到 {ADMIN_DIST}/index.html —— 先构建，或去掉 --dry-run')

    with Server() as srv:
        files = local_entries(ADMIN_DIST)
        print(f'[比对] 本地 {len(files)} 个文件')
        try:
            names = srv.run(f'ls -1 {REMOTE_ADMIN}')[0].splitlines()
            print(f'       远程 {REMOTE_ADMIN} 顶层 {len(names)} 项：{names[:6]}')
        except Exception:
            print(f'       ⚠️ 读不到 {REMOTE_ADMIN}（可能还不存在，上传时会创建）')

        send, skip = plan_uploads(files, lambda rel: srv.size(f'{REMOTE_ADMIN}/{rel}'))
        print(f'       需上传 {len(send)}，可跳过 {len(skip)}')
        for rel in send:
            print(f'         ↑ {rel}')

        if dry_run:
            print('[dry-run] 到此为止，什么都没传。')
            return

        # 与 storefront 一样先备份 index.html —— 回滚就是把它拷回去
        stamp = time.strftime('%Y%m%d-%H%M%S')
        bak = f'{REMOTE_ADMIN}/index.html.bak-{stamp}'
        out, err = srv.run(f'cp -p {REMOTE_ADMIN}/index.html {bak} 2>/dev/null; echo ok')
        if out != 'ok':
            sys.exit(f'备份 index.html 失败：{err or out}')
        print(f'[备份] {bak}（回滚 = 把它拷回 index.html）')

        sent = 0
        for rel in send:
            remote = f'{REMOTE_ADMIN}/{rel}'
            srv.mkdirs(remote.rsplit('/', 1)[0])
            srv.put(ADMIN_DIST / rel, remote)
            sent += 1
        print(f'[上传] 完成 {sent} 个文件')

        verify(srv)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--skip-build', action='store_true', help='复用已有 dist/，只上传')
    ap.add_argument('--dry-run', action='store_true', help='只列会传哪些，不落盘')
    args = ap.parse_args()
    deploy(skip_build=args.skip_build, dry_run=args.dry_run)


if __name__ == '__main__':
    main()
