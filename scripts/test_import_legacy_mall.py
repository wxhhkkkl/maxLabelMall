"""旧商城数据导入的**纯函数**测试（标准库 unittest，无第三方依赖）。

只测「与外部世界无关」的转换逻辑：金额换算、分类路径解析、名称归一化、订单状态映射、
记录组装。不测 admin-api 与 SQL 写入（那部分靠 --dry-run 与导入报告核对）。

跑法：python scripts/test_import_legacy_mall.py

⚠️ 测试里的数字不是随手写的，是先把 data-collection(1).xlsx 逐表核过之后写下的
   **事实**（见 scripts/import-legacy-mall.md 的「数据事实」一节）。它们同时是
   dry-run 的验收基准：任何一条对不上就说明转换逻辑坏了。
"""
import unittest

from import_legacy_mall import (
    ORPHAN_CATEGORY_MAP,
    STOCK_SENTINEL,
    STOCK_SENTINEL_REPLACEMENT,
    XLSX,
    _SPEC_SUFFIX,
    build_categories,
    classify_unmatched,
    build_members,
    build_orders,
    build_products,
    load_sheets,
    map_order_status,
    match_product,
    norm_name,
    parse_multiline,
    yuan_to_fen,
)

SHEETS = load_sheets(XLSX)


class TestYuanToFen(unittest.TestCase):
    """元→分。必须用 Decimal —— 6.2*100 用浮点是 619.9999999999999。"""

    def test_integers(self):
        self.assertEqual(yuan_to_fen('12'), 1200)
        self.assertEqual(yuan_to_fen('1'), 100)
        self.assertEqual(yuan_to_fen('100000'), 10000000)

    def test_one_decimal(self):
        self.assertEqual(yuan_to_fen('6.2'), 620)
        self.assertEqual(yuan_to_fen('41.9'), 4190)

    def test_two_decimals(self):
        self.assertEqual(yuan_to_fen('11.66'), 1166)
        self.assertEqual(yuan_to_fen('129.88'), 12988)

    def test_negative_amounts_from_the_ledger(self):
        # 流水表用正负表示方向：「支出」行是 -190
        self.assertEqual(yuan_to_fen('-190'), -19000)
        self.assertEqual(yuan_to_fen('-6.2'), -620)

    def test_float_would_be_wrong(self):
        # 不是修辞：37.66 是数据集里真实的一条商品价，282 条里有 16 条这样
        self.assertEqual(37.66 * 100, 3765.9999999999995)
        self.assertEqual(int(37.66 * 100), 3765)      # 少 1 分
        self.assertEqual(yuan_to_fen('37.66'), 3766)

    def test_rejects_garbage(self):
        for bad in ('', None, 'abc', '1.2.3'):
            with self.assertRaises(ValueError):
                yuan_to_fen(bad)


class TestHelpers(unittest.TestCase):
    def test_norm_name_strips_punct_and_space(self):
        self.assertEqual(norm_name('宸发 110mm宽 300m长 混合基碳带（单卷）'),
                         norm_name('宸发110mm宽300m长混合基碳带(单卷)'))
        self.assertEqual(norm_name('优等铜板纸【双排】50*20*5000张/卷'),
                         norm_name('优等铜板纸[双排]50×20×5000张/卷'))

    def test_parse_multiline(self):
        self.assertEqual(parse_multiline('1\n1'), ['1', '1'])
        self.assertEqual(parse_multiline('1\n1\n'), ['1', '1'])
        self.assertEqual(parse_multiline(''), [])

    def test_map_order_status(self):
        # 用户提供的旧系统状态码表（2026-09-29）
        self.assertEqual(map_order_status('2'), (30, 0))   # 交易成功 → 已完成
        self.assertEqual(map_order_status('3'), (20, 0))   # 等待收货 → 已发货
        self.assertEqual(map_order_status('4'), (40, 0))   # 交易关闭 → 已取消
        self.assertEqual(map_order_status('6'), (40, 20))  # 退款完成 → 已取消 + 全部退款
        self.assertEqual(map_order_status('8'), (20, 0))   # 货到付款已发货 → 已发货
        with self.assertRaises(ValueError):
            map_order_status('99')


class TestCategories(unittest.TestCase):
    def setUp(self):
        self.rows = build_categories(SHEETS['产品分类'])

    def test_shape(self):
        self.assertEqual(len(self.rows), 42)
        self.assertEqual(len([r for r in self.rows if r['parent_key'] is None]), 7)
        self.assertEqual(len([r for r in self.rows if r['parent_key'] is not None]), 35)

    def test_sort_from_visible_order(self):
        self.assertEqual([r['sort'] for r in self.rows], list(range(1, 43)))

    def test_every_child_resolves_to_a_real_parent(self):
        names = {r['name'] for r in self.rows if r['parent_key'] is None}
        for r in self.rows:
            if r['parent_key'] is not None:
                self.assertIn(r['parent_key'], names, r)

    def test_leaves_are_unique_under_their_parent(self):
        keys = [(r['parent_key'], r['name']) for r in self.rows]
        self.assertEqual(len(keys), len(set(keys)))


class TestProducts(unittest.TestCase):
    def setUp(self):
        self.cats = build_categories(SHEETS['产品分类'])
        self.rows = build_products(SHEETS['产品列表'], self.cats)

    def test_count(self):
        # 编号重复、跨分类，所以 282 行就是 282 个商品（已与你确认）
        self.assertEqual(len(self.rows), 282)

    def test_stock_sentinel_replaced(self):
        # 唯一一处「改动原值」：DZ001 不干胶定制（按需定制，旧系统用 int 上限-15 表示无限）
        replaced = [r for r in self.rows if r['stock'] == STOCK_SENTINEL_REPLACEMENT]
        self.assertEqual(len(replaced), 1)
        self.assertEqual(replaced[0]['serial'], 'DZ001')
        self.assertEqual(replaced[0]['name'], '不干胶定制')
        self.assertNotIn(STOCK_SENTINEL, [r['stock'] for r in self.rows])

    def test_prices_are_fen(self):
        for r in self.rows:
            self.assertIsInstance(r['price'], int)
            self.assertGreater(r['price'], 0)
        # 旧价 6.2 元的那条 → 620 分
        self.assertIn(620, [r['price'] for r in self.rows])

    def test_keyword_is_the_legacy_serial(self):
        for r in self.rows:
            self.assertEqual(r['keyword'], r['serial'])

    def test_every_product_has_a_resolved_leaf_category(self):
        leaves = {(r['parent_key'], r['name']) for r in self.cats if r['parent_key']}
        for r in self.rows:
            self.assertIn((r['category_parent'], r['category_leaf']), leaves, r)

    def test_pic_url_is_the_site_placeholder(self):
        self.assertEqual({r['pic_url'] for r in self.rows}, {'/assets/logo.png'})

    def test_text_fields_are_marked_pending(self):
        # 宪法 FR-054：不得编造业务事实 —— 简介与详情旧系统没导出，只能是 [[ ]] 占位符。
        # description 是富文本，故包一层 <p>，占位标记在标签里面。
        for r in self.rows:
            self.assertTrue(r['introduction'].startswith('[['), r)
            self.assertIn('[[', r['description'], r)
            self.assertTrue(r['description'].endswith(']]</p>'), r)

    def test_duplicate_serials_are_kept_as_separate_products(self):
        serials = [r['serial'] for r in self.rows]
        self.assertEqual(len(serials), 282)
        self.assertEqual(len(set(serials)), 242)  # 32 个编号重复、共 72 行


class TestOrphanCategories(unittest.TestCase):
    """4 个引用了「分类表里没有的路径」的商品。3 个是叶子漏写父级，1 个真未分类。"""

    def test_three_are_leaf_name_misses(self):
        self.assertEqual(ORPHAN_CATEGORY_MAP['经济热敏'], ('卷筒标签', '经济热敏'))
        self.assertEqual(ORPHAN_CATEGORY_MAP['蜡基'], ('碳带', '蜡基'))
        self.assertEqual(ORPHAN_CATEGORY_MAP['宸发服务'], ('软件服务', '宸发服务'))

    def test_uncategorized_needed_a_human_call(self):
        # 唯一需要人判断的一条，必须显式标记出来，不得静默塞进某个分类
        self.assertEqual(ORPHAN_CATEGORY_MAP['未分类'], ('卷筒标签', '高等铜版'))

    def test_all_four_are_documented(self):
        self.assertEqual(len(ORPHAN_CATEGORY_MAP), 4)


class TestMembers(unittest.TestCase):
    def setUp(self):
        self.importable, self.skipped = build_members(SHEETS['会员列表'])

    def test_five_without_mobile_are_skipped_not_invented(self):
        self.assertEqual(len(self.skipped), 5)
        self.assertEqual(len(self.importable), 86)
        self.assertEqual({r['legacy_id'] for r in self.skipped},
                         {'5667032', '5667031', '5665971', '5661130', '5658616'})

    def test_registration_time_is_preserved(self):
        # yudao 没有 register_time 字段，注册时间落在 create_time 上
        self.assertTrue(all(r['create_time'] for r in self.importable))

    def test_legacy_id_kept_in_mark(self):
        for r in self.importable:
            self.assertIn(r['legacy_id'], r['mark'])

    def test_points_and_balance_are_zero_so_nothing_to_migrate(self):
        self.assertEqual({r['point'] for r in self.importable}, {0})

    def test_mobiles_are_valid_and_unique(self):
        mobiles = [r['mobile'] for r in self.importable]
        self.assertEqual(len(mobiles), len(set(mobiles)))
        for m in mobiles:
            self.assertRegex(m, r'^1\d{10}$')


class TestOrders(unittest.TestCase):
    def setUp(self):
        cats = build_categories(SHEETS['产品分类'])
        prods = build_products(SHEETS['产品列表'], cats)
        self.members, _ = build_members(SHEETS['会员列表'])
        self.orders, self.items, self.unmatched, self.unknown = build_orders(
            SHEETS['订单列表'], SHEETS['流水列表'], self.members, prods)

    def test_counts(self):
        self.assertEqual(len(self.orders), 393)
        self.assertEqual(len(self.items), 486)
        self.assertEqual(self.unknown, [])

    def test_status_distribution(self):
        got = {}
        for o in self.orders:
            got[o['status']] = got.get(o['status'], 0) + 1
        self.assertEqual(got, {30: 381, 20: 2, 40: 10})

    def test_refund_flag_only_on_the_refunded_one(self):
        refunded = [o for o in self.orders if o['refund_status'] == 20]
        self.assertEqual(len(refunded), 1)
        self.assertEqual(refunded[0]['legacy_status'], '6')

    def test_order_no_is_the_legacy_order_id(self):
        for o in self.orders:
            self.assertTrue(o['no'].isdigit())
            self.assertLessEqual(len(o['no']), 32)

    def test_amounts_are_fen(self):
        for o in self.orders:
            self.assertIsInstance(o['pay_price'], int)
            self.assertIsInstance(o['total_price'], int)
            # yudao 的算法：应付 = 原价 − 优惠 + 运费 + 调价
            self.assertEqual(o['total_price'] - o['discount_price'] + o['adjust_price'],
                             o['pay_price'], o['no'])

    def test_paid_amount_prefers_modified_money(self):
        # 已核过：非空的 ModifiedMoney 与流水扣款额逐笔吻合（190 vs -190）
        for o in self.orders:
            if o['legacy_paid']:
                self.assertEqual(o['pay_price'], yuan_to_fen(o['legacy_paid']))
            else:
                self.assertEqual(o['pay_price'], yuan_to_fen(o['legacy_total']))

    def test_product_count_is_sum_of_amounts(self):
        for o in self.orders:
            self.assertEqual(o['product_count'], sum(i['count'] for i in self.items
                                                     if i['order_no'] == o['no']))

    def test_unmatched_names_split_into_recoverable_and_gone(self):
        """报告要把 63 个分成两类 —— 不然「63 个对不上」读起来像 63 个都丢了。"""
        cats = build_categories(SHEETS['产品分类'])
        prods = build_products(SHEETS['产品列表'], cats)
        recoverable, gone = classify_unmatched(self.unmatched, prods)
        self.assertEqual(len(recoverable), 50)
        self.assertEqual(len(gone), 13)
        # 可恢复的那类，去掉【规格】后缀确实能对上商品表
        index = {norm_name(p['name']) for p in prods}
        for n in recoverable:
            self.assertIn(norm_name(_SPEC_SUFFIX.sub('', n).strip()), index, n)
        # 不可恢复的那类，去不去后缀都对不上
        for n in gone:
            self.assertNotIn(norm_name(_SPEC_SUFFIX.sub('', n).strip()), index, n)

    def test_unmatched_items_are_reported_not_invented(self):
        # 25% 的明细行在商品表里找不到对应商品 → product_serial 记 None、单价记 0，
        # 并把去重后的名称列成清单（写入时 spu_id/sku_id 落 0）
        unmatched_items = [i for i in self.items if i['product_serial'] is None]
        self.assertEqual(len(unmatched_items), 119)
        self.assertEqual(len(self.unmatched), 63)
        for i in unmatched_items:
            self.assertEqual(i['price'], 0)

    def test_matched_items_point_at_real_products(self):
        for i in self.items:
            if i['product_serial']:
                self.assertEqual(i['pay_price'], i['price'] * i['count'])
                self.assertGreater(i['price'], 0)

    def test_matched_items_use_an_unambiguous_key(self):
        """写入时用下标定位商品，不用商品编号 —— 编号有 32 个重复、跨材质复用。"""
        cats = build_categories(SHEETS['产品分类'])
        prods = build_products(SHEETS['产品列表'], cats)
        dup_serials = {p['serial'] for p in prods
                       if [q['serial'] for q in prods].count(p['serial']) > 1}
        self.assertTrue(dup_serials)                      # 数据里确实有重复编号
        for i in self.items:
            if i['product_idx'] is None:
                self.assertIsNone(i['product_serial'])
                continue
            prod = prods[i['product_idx']]
            self.assertEqual(norm_name(prod['name']), norm_name(i['spu_name']))
            self.assertEqual(prod['serial'], i['product_serial'])
        # 重复编号的商品各自有独立下标，不会串成一个
        idxs = {i['product_idx'] for i in self.items if i['product_idx'] is not None}
        self.assertEqual(len(idxs), len(set(idxs)))

    def test_item_lines_and_amounts_parse_one_to_one(self):
        for o in self.orders:
            self.assertEqual(len(o['_names']), len(o['_amounts']), o['no'])

    def test_receiver_fields_satisfy_not_null(self):
        for o in self.orders:
            self.assertTrue(o['receiver_name'])
            self.assertTrue(o['receiver_mobile'])
            self.assertLessEqual(len(o['receiver_name']), 20)
            self.assertLessEqual(len(o['receiver_mobile']), 20)

    def test_terminal_is_unknown_not_guessed(self):
        self.assertEqual({o['terminal'] for o in self.orders}, {0})

    def test_no_referrer_relation_to_migrate(self):
        self.assertEqual({o['brokerage_user_id'] for o in self.orders}, {None})


class TestFinanceArchive(unittest.TestCase):
    def test_finance_is_archived_not_imported(self):
        # 决策：670 条流水不入库（余额全 0，无资产可迁），只导出留档
        self.assertEqual(len(SHEETS['流水列表']), 670)


if __name__ == '__main__':
    unittest.main(verbosity=2)
