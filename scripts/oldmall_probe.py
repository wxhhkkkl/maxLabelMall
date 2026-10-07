"""旧商城后台连通性探查（只读，不落库）。

凭据从仓库根目录 .env 读取，**不回显**。

用法：
    python scripts/oldmall_probe.py            # 阶段一：抓验证码图，存到 .cache/vcode.png
    python scripts/oldmall_probe.py 4nsk       # 阶段二：带验证码登录，打印会话与接口线索
"""
import hashlib
import re
import sys
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "oldMallData" / ".cache"

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)


def load_env():
    env = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip()
    return env


def new_session(env):
    admin_url = env["OLD_MALL_MANAGE_HREF"]
    base = re.match(r"(https?://[^/]+)", admin_url).group(1)
    s = requests.Session()
    s.headers["User-Agent"] = UA
    s.get(admin_url, timeout=30)
    return s, base, admin_url


def save_cookies(s):
    CACHE.mkdir(parents=True, exist_ok=True)
    (CACHE / "cookies.txt").write_text(
        "\n".join(f"{c.name}\t{c.value}" for c in s.cookies), encoding="utf-8"
    )


def load_cookies(s):
    f = CACHE / "cookies.txt"
    if not f.exists():
        return False
    for line in f.read_text(encoding="utf-8").splitlines():
        if "\t" in line:
            k, v = line.split("\t", 1)
            s.cookies.set(k, v)
    return True


def main():
    env = load_env()
    s, base, admin_url = new_session(env)

    if len(sys.argv) < 2:
        CACHE.mkdir(parents=True, exist_ok=True)
        r = s.get(base + "/index.php?c=admin/login&a=validateCode&rand=0.5", timeout=30)
        (CACHE / "vcode.png").write_bytes(r.content)
        save_cookies(s)
        print("captcha saved:", CACHE / "vcode.png", r.status_code, len(r.content), "bytes")
        return 2

    if not load_cookies(s):
        print("no cookie jar; run without args first")
        return 1

    pwd_md5 = hashlib.md5(env["OLD_MALL_ADMIN_PSD"].encode()).hexdigest()
    r = s.post(
        base + "/index.php?c=admin/login&a=login",
        data={
            "UserName": env["OLD_MALL_ADMIN_USER"],
            "Password": pwd_md5,
            "Domain": "",
            "Vcode": sys.argv[1],
            "token": "0.5",
            "isMd5": 1,
            "_tid": "0.654321",
        },
        headers={"Referer": admin_url},
        timeout=30,
    )
    try:
        j = r.json()
    except Exception:
        print("LOGIN non-json:", r.status_code, r.text[:300])
        return 1
    print("login success =", j.get("success"), "| msg =", j.get("msg"), "| redirect =", j.get("redirect"))
    if not j.get("success"):
        return 1
    save_cookies(s)
    CACHE.mkdir(parents=True, exist_ok=True)

    idx = s.get(base + "/admin/index.html", timeout=30, headers={"Referer": admin_url})
    print("index:", idx.status_code, len(idx.text))
    CACHE.joinpath("index.html").write_text(idx.text, encoding="utf-8")
    eps = sorted(set(re.findall(r"[\"'\(]([a-zA-Z0-9_/\.\-]*index\.php\?c=[^\"'\)\s]+)", idx.text)))
    for m in eps[:60]:
        print("  endpoint:", m)
    assets = sorted(set(re.findall(r"(?:src|href)=\"([^\"]+\.(?:js|html))\"", idx.text)))
    for m in assets[:60]:
        print("  asset:", m)
    return 0


if __name__ == "__main__":
    sys.exit(main())
