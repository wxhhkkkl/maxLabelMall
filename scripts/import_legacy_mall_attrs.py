#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""旧商城「多规格」→ 线上商品（yudao，租户 162 御旺宸发）。

背景
----
首轮导入（`import_legacy_mall.py`）把 282 个商品全建成了**单规格**。原因不是脚本写错，
而是当时抓的 `GetProductInfo` 接口对全部 282 个商品返回 `norms: []` —— 规格根本不在
那个接口里。2026-10-12 找到真正的来源（`loadProductAttr`，见
`scrape_old_mall_attrs.py`）后重抓：**282 个商品里 9 个有多规格，合计 189 个 SKU**。

前台代码本来就支持多规格（详情页规格按钮、价格库存联动、加购下单按所选 skuId），
所以这里**只补数据**，不碰前端规格逻辑。

为什么要走 admin-api 而不是直写 SQL
----------------------------------
`ProductSpuServiceImpl.updateSpu` 里有两条服务端裁决的规则：
  · `initSpuFromSkus`：SPU 的 price/marketPrice/costPrice 取**所有 SKU 的最小值**、
    stock 取**所有 SKU 之和**；
  · `ProductSkuService.validateSkuList`：规格组/规格值必须先存在（否则 `PROPERTY_NOT_EXISTS`），
    SKU 之间 valueId 组合不许重复。
用 SQL 手写等于把这两条规则抄一遍（宪法原则 III 禁止）。所以沿用首轮导入的做法。

一个必须小心的坑
----------------
`ProductSpuServiceImpl.updateSpu` 末尾会调 `saveSpuFilterValues`，而它是**全量替换** ——
`filterValueIds` 传空会把该 SPU 已有的「筛选维度」标签清光（那 9 个商品目前标签数为 0，
但这不等于以后也是 0）。所以本脚本**从 get-detail 原样带回 filterValueIds**。

凭据
----
后台开着滑块验证码，所以 accessToken 由使用者从浏览器已登录的后台复制
（`--token` 或环境变量 `MAXLABEL_ADMIN_TOKEN`）。注意线上这个账号属于**租户 162 本身**，
请求头用 `tenant-id: 162`，**不要**带 `visit-tenant-id`（带了会 403「您无权访问该租户的数据」）。

跑法
----
    python scripts/import_legacy_mall_attrs.py dry-run                 # 只打印对照，不发请求
    python scripts/import_legacy_mall_attrs.py apply --token <token> --yes

回滚
----
apply 前会把 9 个 SPU 的 get-detail 原样存进
`scripts/_backup/spu-before-attrs-<时间戳>.json`；回滚 = 用那份快照按单规格 PUT 回去。
"""
import argparse
import datetime
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from import_legacy_mall import STOCK_SENTINEL, yuan_to_fen  # noqa: E402

# 控制台编码兜底：下面要打印 ¥，Windows 默认按 GBK 输出会直接 UnicodeEncodeError ——
# 崩在导入中途比乱码难受得多，所以先兜住（与 deploy.py 同一套做法）。
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

OUT = ROOT / "oldMallData"
DATA = OUT / "data"
SCRIPTS = ROOT / "scripts"
MANIFEST = SCRIPTS / "import-legacy-mall-manifest.json"
BACKUP_DIR = SCRIPTS / "_backup"

ADMIN_BASE = "https://new.yuwangchenfa.com/admin-api"
TENANT_ID = 162
FALLBACK_PIC = "/assets/logo.png"


# ══════════════════════════════════════════════════════════════════════════════
# 纯函数区（见 test_import_legacy_mall_attrs.py）
# ══════════════════════════════════════════════════════════════════════════════

def resolve_spu_ids(created, products, attrs):
    """旧 ProductID → 新 SPU id。

    键取 `(旧编号 SerialNum, 旧名称 Name)` —— 单看 SerialNum 不行（282 个里有 32 个重复），
    单看名称也不保险。对不上或对到多个一律报错，**不许猜**。
    """
    index = {}
    for c in created:
        index.setdefault((c["serial"], c["name"]), []).append(c["id"])
    by_pid = {p["ProductID"]: p for p in products}

    out = {}
    for a in attrs:
        pid = str(a["ProductID"])
        p = by_pid.get(pid)
        if p is None:
            raise ValueError(f"旧商品 {pid} 不在 products.json 里")
        key = (p["SerialNum"], p["Name"])
        hits = index.get(key, [])
        if len(hits) != 1:
            raise ValueError(f"旧商品 {pid} 的 (编号,名称) 对到 {len(hits)} 个新 SPU，无法唯一确定：{key}")
        out[pid] = hits[0]
    return out


def parse_path(path):
    """旧 SKU 的 `Path`（形如 `,1527156,`）→ 规格值 id 列表。"""
    if not isinstance(path, str):
        raise ValueError(f"Path 不是字符串: {path!r}")
    return [x for x in path.strip(",").split(",") if x]


def check_single_attrkey(attrkeys):
    """这 9 个商品都只有 **1 个**规格组。多组意味着规格值要做笛卡尔组合，
    而旧数据只给了 SKU 的实际组合 —— 猜不得，直接报错。"""
    if len(attrkeys) != 1:
        raise ValueError(f"商品有 {len(attrkeys)} 个规格组，本脚本只支持单规格组（不猜组合关系）")


def build_sku_specs(attrkey, productSkus, image_urls, fallback_pic=FALLBACK_PIC):
    """把旧 SKU 列表转成「与规格值一一对应」的中间结构。

    返回 [{value_name, old_value_id, price, cost_price, stock, pic_url}]，顺序按旧 SKU。
    """
    value_by_id = {str(v["AttrValID"]): v["AttrValName"] for v in attrkey.get("attrVals") or []}
    if not value_by_id:
        raise ValueError("规格组没有任何规格值")

    specs, seen = [], set()
    for sku in productSkus:
        ids = parse_path(sku["Path"])
        if len(ids) != 1:
            raise ValueError(f"SKU {sku.get('SkuID')} 的 Path 含 {len(ids)} 个规格值，与单规格组不符")
        val_id = ids[0]
        if val_id not in value_by_id:
            raise ValueError(f"SKU {sku.get('SkuID')} 引用了规格组里不存在的规格值 {val_id}")
        if val_id in seen:
            raise ValueError(f"规格值 {val_id} 被两个 SKU 同时使用，无法一一对应")
        seen.add(val_id)

        stock = int(sku["ProductQuantity"])
        if stock == STOCK_SENTINEL:
            raise ValueError(f"SKU {sku.get('SkuID')} 的库存是旧系统的「无限库存」哨兵值，需人工确认")

        specs.append({
            "value_name": value_by_id[val_id],
            "old_value_id": val_id,
            "price": yuan_to_fen(sku["Price"]),
            "cost_price": yuan_to_fen(sku["CostPrice"]) if sku.get("CostPrice") else 0,
            "stock": stock,
            "pic_url": (image_urls or {}).get(val_id) or fallback_pic,
        })
    return specs


def merge_collisions(specs, value_id_map):
    """把"塌成同一条"的规格值合并成一个 SKU。

    旧数据里存在**逐字节同名**的两个规格值（实测 SPU 711 有两个
    `50*25*2500双排`）。新库的 `createPropertyValue` 按名字去重（且 MySQL 的
    `utf8mb4_general_ci` 是 PAD SPACE、不区分大小写），两个旧值会拿到**同一个**
    valueId —— 于是两个 SKU 的 valueId 组合相同，服务端直接报
    「一个 SPU 下的每个 SKU，必须不重复」。

    这里不自作聪明去模拟 MySQL 的排序规则，而是**看服务端给回来的 id**：
    只要两个旧规格值拿到同一个新 id，就是塌了。

    合并规则（2026-10-12 与所有者确认）：**保留先出现的那个，库存取它的值，不累加**
    —— 逐字节同名的两条更可能是同一条被录了两遍。但只在被丢弃的那几条
    价格/成本/库存**完全一致**时才合并，只要有一项不同就报错交人判断，绝不静默丢数据。

    :return: (保留的 specs, {新valueId: [被丢弃的旧valueId]})
    """
    by_new_id, kept, dropped = {}, [], {}
    for s in specs:
        new_id = value_id_map.get(s["old_value_id"])
        if new_id is None:
            raise ValueError(f"规格值 {s['old_value_id']} 还没有 id")
        first = by_new_id.get(new_id)
        if first is None:
            by_new_id[new_id] = s
            kept.append(s)
            continue
        for field in ("price", "cost_price", "stock"):
            if first[field] != s[field]:
                raise ValueError(
                    f"规格值 {first['old_value_id']} 与 {s['old_value_id']} 在新库里是同一条"
                    f"（同名），但 {field} 不同（{first[field]} vs {s[field]}）——"
                    f"不能自动合并，需要人工确认保留哪一个")
        dropped.setdefault(new_id, []).append(s["old_value_id"])
    return kept, dropped


def to_sku_payload(specs, property_id, property_name, value_id_map):
    """中间结构 → `ProductSkuSaveReqVO` 列表。

    `value_id_map` 是 {旧规格值id: 新建后的规格值id}，必须先建好规格值才能填。
    """
    out = []
    for s in specs:
        new_value_id = value_id_map.get(s["old_value_id"])
        if new_value_id is None:
            raise ValueError(f"规格值 {s['old_value_id']} 还没有 id")
        out.append({
            "name": s["value_name"],
            "price": s["price"],
            "marketPrice": 0,
            "costPrice": s["cost_price"],
            "stock": s["stock"],
            "weight": 0,
            "picUrl": s["pic_url"],
            "properties": [{
                "propertyId": property_id,
                "propertyName": property_name,
                "valueId": new_value_id,
                "valueName": s["value_name"],
            }],
        })
    return out


# update 接口的必填字段；全部从现有 get-detail 原样带回，只改 specType 与 skus
_CARRY_FIELDS = (
    "name", "keyword", "introduction", "description", "categoryId", "brandId",
    "picUrl", "sliderPicUrls", "sort", "deliveryTypes", "giveIntegral",
    "subCommissionType", "virtualSalesCount", "salesCount", "browseCount",
    "deliveryTemplateId",
)


def build_spu_update_payload(detail, skus):
    """现有 SPU 明细 + 新 SKU → update 请求体。

    ⚠️ `filterValueIds` 必须原样带回：updateSpu 末尾的 `saveSpuFilterValues` 是
    全量替换，传空/不传会把该商品已配的筛选维度标签清掉。
    """
    payload = {k: detail.get(k) for k in _CARRY_FIELDS if detail.get(k) is not None}
    payload["id"] = detail["id"]
    payload["specType"] = True
    payload["skus"] = skus
    payload["filterValueIds"] = detail.get("filterValueIds") or []
    return payload


def stock_cross_check(old_parent_stock, specs):
    """旧站父商品的库存 = 各 SKU 之和（9 个里 8 个完全相等）。不一致就报出来给人看。"""
    total = sum(s["stock"] for s in specs)
    return {"legacy_parent": old_parent_stock, "sku_sum": total,
            "match": old_parent_stock == total, "diff": old_parent_stock - total}


# ══════════════════════════════════════════════════════════════════════════════
# admin-api 客户端
# ══════════════════════════════════════════════════════════════════════════════

class AdminApi:
    def __init__(self, token, base=ADMIN_BASE):
        self.base = base
        self.token = token

    def call(self, method, path, body=None):
        req = urllib.request.Request(self.base + path, method=method)
        req.add_header("tenant-id", str(TENANT_ID))   # 本 token 属于租户 162 本身
        req.add_header("Authorization", "Bearer " + self.token)
        data = None
        if body is not None:
            req.add_header("Content-Type", "application/json; charset=utf-8")
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        try:
            with urllib.request.urlopen(req, data, timeout=60) as r:
                res = json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            raise SystemExit(f"✗ {method} {path} HTTP {e.code}: {e.read().decode('utf-8')[:300]}")
        if res.get("code") != 0:
            raise SystemExit(f"✗ {method} {path} 失败: {res.get('msg')}")
        return res.get("data")

    def spu_detail(self, spu_id):
        return self.call("GET", f"/product/spu/get-detail?id={spu_id}")

    def create_property(self, name):
        return self.call("POST", "/product/property/create", {"name": name})

    def create_property_value(self, property_id, name):
        return self.call("POST", "/product/property/value/create",
                         {"propertyId": property_id, "name": name})

    def update_spu(self, payload):
        return self.call("PUT", "/product/spu/update", payload)


# ══════════════════════════════════════════════════════════════════════════════
# 命令行
# ══════════════════════════════════════════════════════════════════════════════

def load_inputs():
    attrs = json.loads((DATA / "product_attrs.json").read_text(encoding="utf-8"))
    products = json.loads((DATA / "products.json").read_text(encoding="utf-8"))
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    img = (DATA / "sku_image_urls.json")
    images = json.loads(img.read_text(encoding="utf-8")) if img.exists() else {}
    return attrs, products, manifest["created"]["product_spu"], images


def plan_all(only=None):
    """返回 [(旧PID, 新SPU id, 规格组名, specs, 旧父库存)]，纯本地计算，不发请求。

    :param only: 只处理这些旧 PID（重试/分批时用），None 表示全部
    """
    attrs, products, created, images = load_inputs()
    if only:
        attrs = [a for a in attrs if str(a["ProductID"]) in only]
        if not attrs:
            raise SystemExit(f"--only 指定的商品一个都没匹配上: {sorted(only)}")
    spu_ids = resolve_spu_ids(created, products, attrs)
    by_pid = {p["ProductID"]: p for p in products}
    plan = []
    for a in attrs:
        pid = str(a["ProductID"])
        check_single_attrkey(a["attrkeys"])
        key = a["attrkeys"][0]
        specs = build_sku_specs(key, a["productSkus"], images.get(pid, {}))
        plan.append((pid, spu_ids[pid], key["AttrKeyName"], specs,
                     int(by_pid[pid]["ProductQuantity"])))
    return plan


def cmd_dry_run(only=None):
    plan = plan_all(only)
    print(f"待改造 {len(plan)} 个商品，合计 {sum(len(s) for _, _, _, s, _ in plan)} 个 SKU\n")
    for pid, spu_id, prop_name, specs, old_stock in plan:
        prices = [s["price"] for s in specs]
        chk = stock_cross_check(old_stock, specs)
        withpic = sum(1 for s in specs if s["pic_url"] != FALLBACK_PIC)
        diff = "" if chk["match"] else "（差 %d）" % chk["diff"]
        print(f"旧 {pid} → SPU {spu_id}")
        print(f"   规格组「{prop_name}」 {len(specs)} 个规格值")
        print(f"   价格区间 ¥{min(prices)/100:.2f} ~ ¥{max(prices)/100:.2f}"
              f"（线上现值会在导入后被改成最低价 ¥{min(prices)/100:.2f}）")
        print(f"   库存 SKU 合计 {chk['sku_sum']} / 旧父商品 {chk['legacy_parent']}{diff}")
        print(f"   带图 SKU {withpic}/{len(specs)}")
    return 0


def cmd_apply(token, confirmed, only=None):
    if not token:
        raise SystemExit("缺 token：--token 或环境变量 MAXLABEL_ADMIN_TOKEN")
    if not confirmed:
        raise SystemExit("写入线上数据要显式确认：加 --yes")

    plan = plan_all(only)
    api = AdminApi(token)

    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    backup_path = BACKUP_DIR / f"spu-before-attrs-{stamp}.json"

    # 1) 先把 9 个 SPU 的现状快照下来，任何一步崩了都能照着回滚
    snapshot = []
    for pid, spu_id, _, _, _ in plan:
        d = api.spu_detail(spu_id)
        if d is None:
            raise SystemExit(f"✗ SPU {spu_id} 不存在（旧商品 {pid}）")
        snapshot.append({"legacy_pid": pid, "detail": d})
        print(f"  快照 SPU {spu_id}：specType={d.get('specType')} 现有 SKU {len(d.get('skus') or [])} 个")
    backup_path.write_text(json.dumps(snapshot, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"[备份] {backup_path}\n")

    # 2) 逐个改造
    for pid, spu_id, prop_name, specs, _ in plan:
        detail = api.spu_detail(spu_id)
        property_id = api.create_property(prop_name)
        value_id_map = {}
        for s in specs:
            value_id_map[s["old_value_id"]] = api.create_property_value(property_id, s["value_name"])
        specs, dropped = merge_collisions(specs, value_id_map)
        for new_id, olds in dropped.items():
            print(f"    ⚠️ 这些旧规格值在新库里是同一条（同名），已合并为一个 SKU：{olds}")
        skus = to_sku_payload(specs, property_id, prop_name, value_id_map)
        api.update_spu(build_spu_update_payload(detail, skus))
        after = api.spu_detail(spu_id)
        prices = [x["price"] for x in (after.get("skus") or [])]
        print(f"[✓] 旧 {pid} → SPU {spu_id}「{prop_name}」"
              f" SKU {len(after.get('skus') or [])} 个，specType={after.get('specType')}，"
              f"SPU 价 ¥{(after.get('price') or 0)/100:.2f}，库存 {after.get('stock')}"
              + (f"（SKU 价区间 ¥{min(prices)/100:.2f}~¥{max(prices)/100:.2f}）" if prices else ""))

    print(f"\n完成。回滚快照：{backup_path}")
    return 0


def main():
    ap = argparse.ArgumentParser(description="把旧商城多规格补迁到线上")
    ap.add_argument("action", choices=["dry-run", "apply"])
    ap.add_argument("--token", default=os.environ.get("MAXLABEL_ADMIN_TOKEN", ""))
    ap.add_argument("--yes", action="store_true", help="确认写入线上数据")
    ap.add_argument("--only", default="", help="只处理这些旧 PID（逗号分隔），便于重试")
    args = ap.parse_args()
    only = {x.strip() for x in args.only.split(",") if x.strip()} or None
    if args.action == "dry-run":
        return cmd_dry_run(only)
    return cmd_apply(args.token, args.yes, only)


if __name__ == "__main__":
    sys.exit(main())
