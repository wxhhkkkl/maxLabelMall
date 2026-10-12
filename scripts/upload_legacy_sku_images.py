#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""旧商城「规格图」：从旧 CDN 下载 → 上传到线上对象存储（阿里云 OSS）。

为什么需要它
------------
旧商城的规格值自带一张 SKU 图（`attrVals[].AttrValBigImg`）。首轮抓取只下了商品主图，
规格图一张没下（`oldMallData/images/` 下 `productSkuImg` 命中 0）。而前台详情页
**切规格要换主图**（2026-10-12 加），所以这 189 张必须补上并落到能公网访问的地方。

为什么不自己搭图床：线上 `infra_file_config` 的 master 配置（id=25）已经是
**阿里云存储**（S3 协议，bucket `maxlabelmall`，域名 `https://img.yuwangchenfa.com`，
公共读）。直接调后台上传接口就会落到 OSS 并返回完整 URL —— **不需要新建任何配置**。

凭据
----
上传走 `POST /admin-api/infra/file/upload`，需要管理端登录态。后台开着滑块验证码，
所以**由使用者从浏览器已登录的后台复制 accessToken**（F12 → Network → 任一
admin-api 请求的 `Authorization: Bearer xxx`）。来源优先级：

    --token <值>  >  环境变量 MAXLABEL_ADMIN_TOKEN

脚本**不保存 token**，也不做任何验证码绕过。

跑法
----
    python scripts/upload_legacy_sku_images.py --download-only
    python scripts/upload_legacy_sku_images.py --token <accessToken>
    python scripts/upload_legacy_sku_images.py --force-upload   # 忽略已有映射重传

断点续跑：图片落 `oldMallData/images/productSkuImg/`，上传结果落
`oldMallData/data/sku_image_urls.json`（`{旧PID: {AttrValID: ossUrl}}`），
重跑时已下/已传的都跳过。
"""
import argparse
import json
import os
import sys
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from oldmall_probe import load_cookies, load_env, new_session  # noqa: E402
from scrape_old_mall import CDN, OldMall, img_candidates  # noqa: E402

# 控制台编码兜底：下面要打印 ✓/✗，Windows 默认按 GBK 输出会直接 UnicodeEncodeError
# （与 deploy.py 同一套做法）
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

OUT = ROOT / "oldMallData"
DATA = OUT / "data"
ATTRS = DATA / "product_attrs.json"
IMG_DIR = OUT / "images" / "productSkuImg"
URL_MAP = DATA / "sku_image_urls.json"

ADMIN_BASE = "https://new.yuwangchenfa.com/admin-api"
TENANT_ID = 162
# 没有规格图时的回落：全站唯一图片资产（与首轮导入 282 个商品用的是同一张）
FALLBACK_PIC = "/assets/logo.png"


def collect_idents(attrs):
    """展平成 [(旧PID, AttrValID, 图片标识)]。优先大图，没有就退回小图。"""
    out = []
    for a in attrs:
        pid = str(a["ProductID"])
        for key in a.get("attrkeys") or []:
            for val in key.get("attrVals") or []:
                ident = val.get("AttrValBigImg") or val.get("AttrValSmallImg")
                out.append((pid, str(val["AttrValID"]), ident))
    return out


def download_images(mall, idents):
    """下载到 IMG_DIR，返回 {AttrValID: 本地路径}。下载不成的进 missing。"""
    local, missing = {}, []
    for i, (pid, val_id, ident) in enumerate(idents, 1):
        url = None
        for cand in img_candidates(ident, hint="productSkuImg"):
            if mall.download(cand, IMG_DIR / cand.rsplit("/", 1)[-1]):
                url = IMG_DIR / cand.rsplit("/", 1)[-1]
                break
        if url:
            local[val_id] = url
        else:
            missing.append({"pid": pid, "attrValId": val_id, "ident": ident})
        if i % 40 == 0 or i == len(idents):
            print(f"  下载 {i}/{len(idents)}，成功 {len(local)}，失败 {len(missing)}")
    return local, missing


def upload_one(session, path, token):
    """上传单个文件，返回可访问 URL。"""
    with open(path, "rb") as f:
        r = session.post(
            ADMIN_BASE + "/infra/file/upload",
            files={"file": (path.name, f, "image/jpeg")},
            # 只用 tenant-id：这个凭据本身就属于租户 162，
            # 再带 visit-tenant-id 会被判「您无权访问该租户的数据」（实测 403）
            headers={"tenant-id": str(TENANT_ID), "Authorization": "Bearer " + token},
            timeout=120,
        )
    j = r.json()
    if j.get("code") != 0:
        raise RuntimeError(f"{r.status_code} {j.get('msg')}")
    return j["data"]


def main():
    ap = argparse.ArgumentParser(description="下载旧站规格图并上传到 OSS")
    ap.add_argument("--token", default=os.environ.get("MAXLABEL_ADMIN_TOKEN", ""),
                    help="后台 accessToken（也可用环境变量 MAXLABEL_ADMIN_TOKEN）")
    ap.add_argument("--download-only", action="store_true", help="只下载，不上传")
    ap.add_argument("--force-upload", action="store_true", help="忽略已有映射，全部重传")
    ap.add_argument("--shard", metavar="I:N", default="1:1",
                    help="只处理第 I 片（共 N 片）。旧 CDN 单线程约 5 秒一张，"
                         "分 4 片并行能把 189 张从 ~16 分钟压到 ~4 分钟。"
                         "分片只影响下载；上传前必须跑一次不带 --shard 的完整流程，"
                         "否则映射会不全")
    args = ap.parse_args()
    i, n = (int(x) for x in args.shard.split(":"))

    attrs = json.loads(ATTRS.read_text(encoding="utf-8"))
    idents = collect_idents(attrs)
    if n > 1:
        idents = idents[i - 1::n]
        print(f"[分片 {i}/{n}] 本次处理 {len(idents)} 张")
    print(f"规格图共 {len(idents)} 张，来自 {len(attrs)} 个多规格商品")

    env = load_env()
    session, base, admin_url = new_session(env)
    load_cookies(session)          # CDN 是公开的，仅复用现成的下载重试/Referer 逻辑
    mall = OldMall(session, base, admin_url)

    local, missing = download_images(mall, idents)
    print(f"下载完成：本地 {len(local)} 张 → {IMG_DIR}")
    if missing:
        print(f"⚠️ {len(missing)} 张下载失败（不编造，导入时回落占位图）：")
        for m in missing[:5]:
            print("   ", json.dumps(m, ensure_ascii=True))

    if args.download_only:
        return 0
    if not args.token:
        print("\n没有 token，跳过上传。传 --token 或设置 MAXLABEL_ADMIN_TOKEN 后重跑。")
        return 1

    url_map = json.loads(URL_MAP.read_text(encoding="utf-8")) if URL_MAP.exists() else {}
    if args.force_upload:
        url_map = {}
    session.headers["User-Agent"] = "maxlabel-import/1.0"

    uploaded = failed = 0
    for val_id, path in sorted(local.items()):
        owner = next(pid for pid, vid, _ in idents if vid == val_id)
        if url_map.get(owner, {}).get(val_id):
            continue
        try:
            url = upload_one(session, path, args.token)
        except Exception as e:
            failed += 1
            print(f"  ✗ 上传失败 {path.name}: {type(e).__name__} {e}")
            continue
        url_map.setdefault(owner, {})[val_id] = url
        uploaded += 1
        if uploaded % 20 == 0:
            URL_MAP.write_text(json.dumps(url_map, ensure_ascii=False, indent=1), encoding="utf-8")
            print(f"  已上传 {uploaded} …")

    URL_MAP.write_text(json.dumps(url_map, ensure_ascii=False, indent=1), encoding="utf-8")
    total = sum(len(v) for v in url_map.values())
    print(f"\n上传完成：本次 {uploaded} 张，失败 {failed} 张，映射共 {total} 条 → {URL_MAP}")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
