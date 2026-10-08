"""backfill_legacy_mall 纯函数层的单测。

只测不碰库的部分（映射、筛选、金额换算）。写库那几步靠 dry-run 人工核对，
不在这里假装覆盖 —— 见 backfill_legacy_mall.py 头部注释。
"""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

import backfill_legacy_mall as bf  # noqa: E402


def order(no, **kw):
    d = {'OrderID': no, 'Address': '', 'DeliveryTime': '', 'LogisticsName': ''}
    d.update(kw)
    return d


# ── ① 收货地址 ────────────────────────────────────────────────────────────────

def test_地址整段写入_不做裁剪():
    """旧地址里省市常和详细地址重复，**故意不裁剪** —— 裁错就丢省市。"""
    addr = '北京市东城区北京市东城区东华门大街1号'
    out = bf.address_updates([order('A1', Address=addr)], {'A1'})
    assert out == [('A1', addr)]


def test_地址只更新库内存在的订单():
    orders = [order('A1', Address='x'), order('A2', Address='y')]
    assert bf.address_updates(orders, {'A1'}) == [('A1', 'x')]


def test_地址为空的跳过():
    assert bf.address_updates([order('A1', Address='   ')], {'A1'}) == []
    assert bf.address_updates([order('A1')], {'A1'}) == []


# ── ② 发货时间 ────────────────────────────────────────────────────────────────

def test_发货时间只取非空且库内存在的():
    orders = [
        order('A1', DeliveryTime='2026-09-24 11:21:41'),
        order('A2', DeliveryTime=''),                       # 没发货
        order('A3', DeliveryTime='2026-09-22 21:17:02'),     # 库内没有
    ]
    assert bf.delivery_updates(orders, {'A1', 'A2'}) == [('A1', '2026-09-24 11:21:41')]


# ── ③ 物流公司 ────────────────────────────────────────────────────────────────

def test_物流返回的是公司名而不是_id():
    """id 要等插入后才知道，纯函数层只回名字。"""
    orders = [order('A1', LogisticsName='申通'), order('A2', LogisticsName='')]
    assert bf.logistics_updates(orders, {'A1', 'A2'}) == [('A1', '申通')]


def test_13家快递公司_名字与快照一致():
    rows = bf.express_rows()
    assert len(rows) == 13
    names = {r['name'] for r in rows}
    assert names == {c['LogisticsName'] for c in bf.load_companies()}


def test_快递编码只填确定的_拿不准的留空():
    """所有者 2026-10-08 决策：不编编码。跨越 / 顺心捷达 是我拿不准的两个。"""
    by_name = {r['name']: r['code'] for r in bf.express_rows()}
    assert by_name['申通'] == 'STO'
    assert by_name['顺丰快递'] == 'SF'
    assert by_name['中通快递'] == 'ZTO'
    assert by_name['韵达'] == 'YD'
    assert by_name['圆通快递'] == by_name['圆通速递'] == 'YTO'
    assert by_name['京东'] == 'JD'
    assert by_name['极兔速递'] == 'JTSD'
    assert by_name['德邦'] == 'DBL'
    assert by_name['安能'] == 'ANE'
    assert by_name['优速'] == 'UC'
    assert by_name['跨越'] == ''
    assert by_name['顺心捷达'] == ''
    # 13 个里只有那 2 个是空的 —— 再多一个空就说明漏填了
    assert sum(1 for r in rows_of_codes() if r == '') == 2


def rows_of_codes():
    return [r['code'] for r in bf.express_rows()]


# ── ④ 漏网订单 ────────────────────────────────────────────────────────────────

def test_快照里正好有2张漏网订单():
    orders = bf.load_orders()
    exist = {o['OrderID'] for o in orders} - {'202609291643048054786',
                                              '202609291519523241867'}
    miss = bf.missing_orders(orders, exist)
    assert {o['OrderID'] for o in miss} == {'202609291643048054786',
                                            '202609291519523241867'}


def test_漏网订单金额与数量按旧值换算():
    """不能自己算单价×数量去凑总额 —— 总额取旧系统原值（它有优惠）。"""
    orders = bf.load_orders()
    miss = [o for o in orders if o['OrderID'] == '202609291643048054786']
    built, items = bf.build_missing(miss, {p: i for i, p in
                                           bf.products_by_legacy_id().items()},
                                    {'5595975': 999})
    o = built[0]
    assert o['total_price'] == 30000            # ¥300.00 → 分
    assert o['pay_price'] == 29000              # ModifiedMoney ¥290.00
    assert o['discount_price'] == 1000          # 优惠 ¥10
    assert o['product_count'] == 10
    assert o['status'] == 30                    # 所有者判定：按已完成补
    assert o['pay_status'] == 1
    assert len(items) == 1
    assert items[0]['price'] == 3000            # 单价 ¥30.00
    assert items[0]['count'] == 10


def test_漏网订单的会员不在库内时直接报错():
    """会员挂不上就不能插 —— 静默插一张没有用户的订单比报错更糟。"""
    miss = [o for o in bf.load_orders() if o['OrderID'] == '202609291643048054786']
    with pytest.raises(SystemExit):
        bf.build_missing(miss, {}, {})


def test_漏网订单的会员确实在已导会员里():
    orders = bf.load_orders()
    miss = [o for o in orders if o['OrderID'] == '202609291643048054786']
    assert miss[0]['UserID'] == '5595975'


# ── 既有约定 ──────────────────────────────────────────────────────────────────

def test_租户写死162():
    assert bf.TENANT_ID == 162


def test_补录状态用的是所有者判定的30_而不是脚本猜的():
    """旧状态码 1 不在 import_legacy_mall.STATUS_MAP 里，这里必须是显式的 30。"""
    from import_legacy_mall import STATUS_MAP
    assert '1' not in STATUS_MAP
    assert bf.MISSING_ORDER_STATUS == 30
