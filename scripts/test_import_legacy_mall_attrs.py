#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""多规格补迁的**纯函数**测试（标准库 unittest，无第三方依赖）。

只测“与外部世界无关”的部分：旧 id 映射、`Path` 解析、SKU 组装、金额换算、
update 请求体的字段透传。不测 admin-api（那部分靠 dry-run 报告与线上读回核对）。

跑法：python scripts/test_import_legacy_mall_attrs.py

⚠️ 夹具全部是**合成**的，不读 `oldMallData/` —— 那个目录已被 .gitignore 忽略
   （含真实客户信息），测试依赖它就会在别人机器上直接失败。
"""
import unittest

from import_legacy_mall_attrs import (
    FALLBACK_PIC,
    build_sku_specs,
    build_spu_update_payload,
    check_single_attrkey,
    merge_collisions,
    parse_path,
    resolve_spu_ids,
    stock_cross_check,
    to_sku_payload,
)


def attrkey(name="规格组", vals=(("100", "100*100"), ("200", "200*200"))):
    return {
        "AttrKeyName": name,
        "attrVals": [{"AttrValID": vid, "AttrValName": vname} for vid, vname in vals],
    }


def sku(sku_id, path, price, cost, qty):
    return {"SkuID": sku_id, "Path": path, "Price": price,
            "CostPrice": cost, "ProductQuantity": qty}


class TestParsePath(unittest.TestCase):
    """旧系统用 `,1527156,` 这种前后带逗号的串表示规格值组合。"""

    def test_single_value(self):
        self.assertEqual(parse_path(",1527156,"), ["1527156"])

    def test_leading_trailing_stripped(self):
        self.assertEqual(parse_path("1527156"), ["1527156"])

    def test_empty(self):
        self.assertEqual(parse_path(","), [])

    def test_rejects_non_string(self):
        with self.assertRaises(ValueError):
            parse_path(None)


class TestCheckSingleAttrkey(unittest.TestCase):
    def test_accepts_one(self):
        check_single_attrkey([attrkey()])          # 不抛就算过

    def test_rejects_zero(self):
        with self.assertRaises(ValueError):
            check_single_attrkey([])

    def test_rejects_two(self):
        # 多规格组要做笛卡尔组合，而旧数据只给了实际卖过的组合 —— 必须报错而不是猜
        with self.assertRaises(ValueError):
            check_single_attrkey([attrkey(), attrkey("另一个组")])


class TestBuildSkuSpecs(unittest.TestCase):
    def test_maps_path_to_value_name_and_converts_money(self):
        specs = build_sku_specs(
            attrkey(),
            [sku("1", ",100,", "37.66", "20.00", 435)],
            {},
        )
        self.assertEqual(len(specs), 1)
        self.assertEqual(specs[0]["value_name"], "100*100")
        self.assertEqual(specs[0]["price"], 3766)      # 浮点会得 3765，少 1 分
        self.assertEqual(specs[0]["cost_price"], 2000)
        self.assertEqual(specs[0]["stock"], 435)

    def test_missing_cost_price_becomes_zero(self):
        specs = build_sku_specs(attrkey(), [sku("1", ",100,", "10.00", "", 1)], {})
        self.assertEqual(specs[0]["cost_price"], 0)

    def test_image_looked_up_by_attrval_id(self):
        specs = build_sku_specs(attrkey(), [sku("1", ",100,", "10.00", "0", 1)],
                                {"100": "https://img.example/a.jpg"})
        self.assertEqual(specs[0]["pic_url"], "https://img.example/a.jpg")

    def test_missing_image_falls_back_to_placeholder(self):
        specs = build_sku_specs(attrkey(), [sku("1", ",100,", "10.00", "0", 1)], {})
        self.assertEqual(specs[0]["pic_url"], FALLBACK_PIC)

    def test_rejects_value_not_in_attrkey(self):
        with self.assertRaises(ValueError):
            build_sku_specs(attrkey(), [sku("1", ",999,", "10.00", "0", 1)], {})

    def test_rejects_two_values_in_single_group(self):
        with self.assertRaises(ValueError):
            build_sku_specs(attrkey(), [sku("1", ",100,200,", "10.00", "0", 1)], {})

    def test_rejects_duplicate_value_across_skus(self):
        # 两个 SKU 抢同一个规格值 → 没法一一对应，必须炸
        with self.assertRaises(ValueError):
            build_sku_specs(attrkey(), [sku("1", ",100,", "10.00", "0", 1),
                                       sku("2", ",100,", "11.00", "0", 1)], {})

    def test_rejects_legacy_infinite_stock_sentinel(self):
        # 2147417040 是旧系统的「无限库存」哨兵，静默当成真实库存会出大事
        with self.assertRaises(ValueError):
            build_sku_specs(attrkey(), [sku("1", ",100,", "10.00", "0", 2147417040)], {})

    def test_rejects_attrkey_without_values(self):
        with self.assertRaises(ValueError):
            build_sku_specs(attrkey(vals=()), [sku("1", ",100,", "10.00", "0", 1)], {})


class TestToSkuPayload(unittest.TestCase):
    def test_builds_properties_with_created_ids(self):
        specs = build_sku_specs(attrkey(), [sku("1", ",100,", "10.00", "5.00", 7)], {})
        payload = to_sku_payload(specs, property_id=501, property_name="规格组",
                                 value_id_map={"100": 9001})
        self.assertEqual(len(payload), 1)
        self.assertEqual(payload[0]["price"], 1000)
        self.assertEqual(payload[0]["marketPrice"], 0)
        self.assertEqual(payload[0]["costPrice"], 500)
        self.assertEqual(payload[0]["stock"], 7)
        self.assertEqual(payload[0]["name"], "100*100")
        self.assertEqual(payload[0]["properties"], [{
            "propertyId": 501, "propertyName": "规格组",
            "valueId": 9001, "valueName": "100*100",
        }])

    def test_rejects_missing_value_id(self):
        specs = build_sku_specs(attrkey(), [sku("1", ",100,", "10.00", "0", 1)], {})
        with self.assertRaises(ValueError):
            to_sku_payload(specs, 501, "规格组", {})


class TestMergeCollisions(unittest.TestCase):
    """旧数据里有两个逐字节同名的规格值（实测 SPU 711）。
    新库按名字建规格值，两者会拿到同一个 valueId → 两个 SKU 撞车报
    「一个 SPU 下的每个 SKU，必须不重复」。"""

    def spec(self, old_id, price=3500, cost=0, stock=100):
        return {"old_value_id": old_id, "value_name": "同名的规格",
                "price": price, "cost_price": cost, "stock": stock,
                "pic_url": FALLBACK_PIC}

    def test_no_collision_keeps_everything(self):
        specs = [self.spec("1"), self.spec("2")]
        kept, dropped = merge_collisions(specs, {"1": 901, "2": 902})
        self.assertEqual([s["old_value_id"] for s in kept], ["1", "2"])
        self.assertEqual(dropped, {})

    def test_same_new_id_merges_and_keeps_first(self):
        specs = [self.spec("1528504"), self.spec("1528505")]
        kept, dropped = merge_collisions(specs, {"1528504": 901, "1528505": 901})
        self.assertEqual([s["old_value_id"] for s in kept], ["1528504"])
        self.assertEqual(dropped, {901: ["1528505"]})

    def test_merges_do_not_sum_stock(self):
        """与所有者确认过：同名两条更可能是同一条录了两遍，库存取先出现那条，不累加。"""
        specs = [self.spec("a", stock=100), self.spec("b", stock=100)]
        kept, _ = merge_collisions(specs, {"a": 901, "b": 901})
        self.assertEqual(kept[0]["stock"], 100)

    def test_refuses_to_merge_when_values_differ(self):
        """价格/成本/库存只要有一项不同，就绝不静默丢掉一条 —— 报错交人判断。"""
        for field, other in (("price", 4000), ("cost", 999), ("stock", 200)):
            with self.subTest(field=field):
                specs = [self.spec("a"),
                         self.spec("b", **{field: other})]
                with self.assertRaises(ValueError):
                    merge_collisions(specs, {"a": 901, "b": 901})

    def test_rejects_missing_value_id(self):
        with self.assertRaises(ValueError):
            merge_collisions([self.spec("a")], {})


class TestBuildSpuUpdatePayload(unittest.TestCase):
    DETAIL = {
        "id": 713, "name": "多规格三防经济热敏纸", "keyword": "JJRM100150",
        "introduction": "[[商品简介，原系统未导出，待补充]]",
        "description": "[[商品详情]]", "categoryId": 102, "brandId": 4,
        "picUrl": "/assets/logo.png",
        "sliderPicUrls": ["https://img.example/a.jpg"],
        "sort": 0, "deliveryTypes": [1], "giveIntegral": 0,
        "subCommissionType": False, "specType": False, "price": 3000, "stock": 41501,
        "salesCount": 12, "browseCount": 34, "virtualSalesCount": 0,
        "filterValueIds": [],
    }

    def test_sets_spec_type_and_swaps_skus(self):
        skus = [{"name": "x", "price": 500}]
        p = build_spu_update_payload(self.DETAIL, skus)
        self.assertIs(p["specType"], True)
        self.assertIs(p["skus"], skus)
        self.assertEqual(p["id"], 713)

    def test_carries_required_fields_verbatim(self):
        p = build_spu_update_payload(self.DETAIL, [])
        for k in ("name", "keyword", "introduction", "description", "categoryId",
                  "brandId", "picUrl", "sliderPicUrls", "sort", "deliveryTypes",
                  "giveIntegral", "subCommissionType"):
            self.assertEqual(p[k], self.DETAIL[k], k)

    def test_keeps_filter_value_ids(self):
        """updateSpu 末尾的 saveSpuFilterValues 是**全量替换** ——
        传空会把该商品已配的筛选维度标签清光，所以必须原样带回。"""
        detail = dict(self.DETAIL, filterValueIds=[11, 22])
        p = build_spu_update_payload(detail, [])
        self.assertEqual(p["filterValueIds"], [11, 22])

    def test_empty_filter_value_ids_still_explicit(self):
        p = build_spu_update_payload(self.DETAIL, [])
        self.assertEqual(p["filterValueIds"], [])

    def test_does_not_invent_missing_optional_fields(self):
        detail = {k: v for k, v in self.DETAIL.items() if k != "deliveryTemplateId"}
        p = build_spu_update_payload(detail, [])
        self.assertNotIn("deliveryTemplateId", p)


class TestResolveSpuIds(unittest.TestCase):
    CREATED = [
        {"id": 713, "serial": "JJRM100150", "name": "多规格三防经济热敏纸"},
        {"id": 721, "serial": "SFRMHS001", "name": "三防彩色热敏纸B"},
    ]
    PRODUCTS = [
        {"ProductID": "10904430", "SerialNum": "JJRM100150", "Name": "多规格三防经济热敏纸"},
        {"ProductID": "10880586", "SerialNum": "SFRMHS001", "Name": "三防彩色热敏纸B"},
    ]

    def test_unique_match(self):
        attrs = [{"ProductID": "10904430"}]
        self.assertEqual(resolve_spu_ids(self.CREATED, self.PRODUCTS, attrs),
                         {"10904430": 713})

    def test_same_serial_different_name_is_not_ambiguous(self):
        """旧库 282 个商品里有 32 个旧编号重复（如 SFRMHS001 被两个商品共用），
        所以键必须是 (编号, 名称) 而不是只编号。"""
        attrs = [{"ProductID": "10880586"}]
        self.assertEqual(resolve_spu_ids(self.CREATED, self.PRODUCTS, attrs),
                         {"10880586": 721})

    def test_ambiguous_raises(self):
        created = self.CREATED + [{"id": 999, "serial": "JJRM100150",
                                   "name": "多规格三防经济热敏纸"}]
        with self.assertRaises(ValueError):
            resolve_spu_ids(created, self.PRODUCTS, [{"ProductID": "10904430"}])

    def test_unknown_product_raises(self):
        with self.assertRaises(ValueError):
            resolve_spu_ids(self.CREATED, self.PRODUCTS, [{"ProductID": "404"}])


class TestStockCrossCheck(unittest.TestCase):
    def test_match(self):
        specs = [{"stock": 100}, {"stock": 23}]
        self.assertTrue(stock_cross_check(123, specs)["match"])

    def test_reports_diff(self):
        specs = [{"stock": 100}, {"stock": 23}]
        chk = stock_cross_check(124, specs)
        self.assertFalse(chk["match"])
        self.assertEqual(chk["diff"], 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
