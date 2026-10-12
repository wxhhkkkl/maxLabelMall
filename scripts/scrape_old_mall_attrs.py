"""旧商城「多规格」数据补抓（`loadProductAttr`）。

为什么需要单独一个脚本
----------------------
首轮全量抓取（`scrape_old_mall.py`）取的是 `GetProductInfo`，那个接口返回的
`norms` / `paramlist` **对全部 282 个商品都是空数组**——规格并不在它里面。
导入脚本据此给每个 SPU 建了唯一一个 SKU（`specType: False`），于是线上 282 个
SPU 全是单规格。2026-10-12 核对确认：`multi-spec` 没迁过来。

规格真正的来源是同模块的另一个动作 `loadProductAttr`：

    /index.php?c=admin/ShopAdmin/product/productmanage&a=loadProductAttr&productid=<PID>

⚠️ 参数名是**全小写的 `productid`**。写成 `id` / `ProductID` / `pid` 都**不报错**，
只是静默返回 `{"attrkeys":[],"productSkus":[]}` —— 很容易误判成"这商品没规格"。
（这个是靠 `all_endpoints.json` 里扫出的动作名 + 逐个参数名试出来的。）

返回结构：
    attrkeys[]     规格组，含 `attrVals[]`（规格值，带名字与 SKU 图）
    productSkus[]  具体 SKU，含 `Price` / `CostPrice` / `ProductQuantity`，
                   以及 `Path`（形如 `,1527156,`，是 attrValID 的组合，用来对回规格值）

设计要点（与 scrape_old_mall.py 一致）：
- **断点续抓**：原始响应落 `.cache/raw/attrs_<pid>.json`，重跑直接复用。
- **会话失效即停**：`OldMall.json` 检测到登录页 / `success=false` 会抛 SessionExpired，
  已抓部分不丢。会话过期先跑 `python scripts/oldmall_probe.py` 重新登录。
- **不编造**：没规格的商品就写进 `without_attrs`，不做任何填充。

跑法：
    python scripts/scrape_old_mall_attrs.py            # 增量补抓
    python scripts/scrape_old_mall_attrs.py --force    # 忽略缓存重抓
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from oldmall_probe import load_cookies, load_env, new_session  # noqa: E402
from scrape_old_mall import OldMall, SessionExpired, write_json  # noqa: E402

OUT = ROOT / "oldMallData"
DATA = OUT / "data"

# 参数名必须是小写 productid，理由见模块头注释
PRODUCT_ATTR = ("/index.php?c=admin/ShopAdmin/product/productmanage"
                "&a=loadProductAttr&productid={pid}")


def main():
    force = "--force" in sys.argv

    env = load_env()
    session, base, admin_url = new_session(env)
    if not load_cookies(session):
        print("没有会话缓存。先跑：python scripts/oldmall_probe.py  （抓验证码并登录）")
        return 1
    mall = OldMall(session, base, admin_url)

    products = json.loads((DATA / "products.json").read_text(encoding="utf-8"))
    print(f"商品 {len(products)} 个，开始抓规格 …")

    with_attrs, without = [], []
    for i, p in enumerate(products, 1):
        pid = str(p["ProductID"])
        try:
            j = mall.cached(f"attrs_{pid}",
                            lambda pid=pid: mall.json(PRODUCT_ATTR.format(pid=pid), method="get"),
                            force=force)
        except SessionExpired as e:
            print(f"\n[停] 会话失效：{e}\n已抓 {len(with_attrs)} 个有规格的商品，"
                  f"重登录后重跑即可续上。")
            break
        d = (j or {}).get("data") or {}
        keys, skus = d.get("attrkeys") or [], d.get("productSkus") or []
        if keys or skus:
            with_attrs.append({
                "ProductID": pid,
                "Name": p.get("Name"),
                "attrkeys": keys,
                "productSkus": skus,
            })
            print(f"  [{i}/{len(products)}] {pid} 规格组={len(keys)} SKU={len(skus)} "
                  f"{json.dumps(p.get('Name'), ensure_ascii=True)[:40]}")
        else:
            without.append(pid)

    write_json(DATA / "product_attrs.json", with_attrs)
    print(f"\n有规格的商品：{len(with_attrs)} 个 → {DATA / 'product_attrs.json'}")
    print(f"无规格的商品：{len(without)} 个（未写入任何占位值）")
    if with_attrs:
        total_sku = sum(len(a["productSkus"]) for a in with_attrs)
        print(f"合计 SKU {total_sku} 个，规格组 "
              f"{sum(len(a['attrkeys']) for a in with_attrs)} 个")
    return 0


if __name__ == "__main__":
    sys.exit(main())
