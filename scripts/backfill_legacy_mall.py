"""旧商城遗留缺口回填（2026-10-08）。

2026-09-29 那次导入只覆盖「分类/商品/会员/订单」四域的**部分字段**，10-04 抓回来的
快照里有数据却一直没入库。这个脚本补四处缺口，全部作用于租户 162：

  ① 393 单的**收货地址** —— 快照 `Address` 有 395 条，库里 `receiver_detail_address` 全 NULL
  ② 286 单的**发货时间** —— 快照 `DeliveryTime` 有 286 条，库里 `delivery_time` 全 NULL
  ③ **13 家快递公司** + 282 单的 `logistics_id` —— 旧站的公司在租户 162 下 0 条
  ④ **2 张漏网订单** + 2 行明细 —— 09-29 下午下单，手工 Excel 里没有，10-04 抓到了没回补

**所有者 2026-10-08 的三项决策**（写死在代码里，改前先看这段）：
  · 地址**整段**写进 `receiver_detail_address`，`receiver_area_id` 留空 —— 不解析省市区。
    理由：393 单的 area_id 本来就是空的、系统照常跑；而旧地址格式极杂
    （「北京市东城区13692012117」这种把电话当地址的、省市重复两遍的），解析错会丢省市，
    而前后台都只做「areaName + detailAddress」拼接，整段写入显示一定完整。
  · 快递 `code` 只填**确定的**快递100标准码，拿不准的（跨越/顺心捷达）留空 —— 不编。
  · 那 2 单的旧状态码是 `1`，不在已确认的 `STATUS_MAP`（2/3/4/6/8）里。
    所有者的判断是**按「已完成」（yudao status 30）补**（它们带 PayTime）。

**明确不做**（所有者 2026-10-08）：文章 34 / 文章分类 48 / 站点页面 53 / 财务流水 674 /
表单 / URL 重写 / SEO / 站点配置 —— 第二、三档全部不导。

用法：
    python scripts/backfill_legacy_mall.py            # dry-run，只打印
    python scripts/backfill_legacy_mall.py --apply    # 真写库（先自动备份）
"""
import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _dbtunnel import DBTunnel, _env  # noqa: E402
from import_legacy_mall import (  # noqa: E402
    DEFAULT_PROPERTIES, ITEM_COLS, ORDER_COLS, TENANT_ID, yuan_to_fen,
)

ROOT = Path(__file__).resolve().parent.parent
SNAP = ROOT / 'oldMallData' / 'data'
BACKUP_DIR = Path(r'C:\Users\liyan\AppData\Local\Temp\ml_deploy')

# 旧状态码 1 → 已完成。**所有者 2026-10-08 判定**，不是脚本推出来的。
# 已确认的那张表（脚本 import_legacy_mall.STATUS_MAP）里没有 1，这里单独记，别混。
# 单量：09-29 16:43 与 15:19 各一单，合计 ¥1650。
MISSING_ORDER_STATUS = 30

# 旧站 13 家快递 → 快递100 编码。
# 空串 = 我不确定，**不编**（所有者 2026-10-08 决策）。
# 前 4 个与库里租户 1 那 4 条一致（STO/SF/ZTO/YD），可直接对照。
EXPRESS_CODES = {
    '申通': 'STO',
    '顺丰快递': 'SF',
    '中通快递': 'ZTO',
    '韵达': 'YD',
    '圆通快递': 'YTO',
    '圆通速递': 'YTO',
    '京东': 'JD',
    '极兔速递': 'JTSD',
    '德邦': 'DBL',
    '安能': 'ANE',
    '优速': 'UC',
    '跨越': '',          # ← 不确定
    '顺心捷达': '',       # ← 不确定
}


def s(v):
    return '' if v is None else str(v).strip()


def load_orders():
    with open(SNAP / 'orders.json', encoding='utf-8') as f:
        return json.load(f)


def load_companies():
    with open(SNAP / 'logistics_companies.json', encoding='utf-8') as f:
        return json.load(f)


# ══════════════════════════════════════════════════════════════════════════════
# 纯函数层（由 test_backfill_legacy_mall.py 覆盖）
# ══════════════════════════════════════════════════════════════════════════════

def express_rows():
    """13 家快递公司 → 待插入的行。`sort` 按快照的 ShowOrder 排，其余用默认值。"""
    rows = []
    for i, c in enumerate(load_companies()):
        name = s(c.get('LogisticsName'))
        rows.append({
            'code': EXPRESS_CODES.get(name, ''),
            'name': name,
            'sort': int(s(c.get('ShowOrder')) or 0) * 100 + i,
            'status': 0,
            'tenant_id': TENANT_ID,
        })
    return rows


def address_updates(orders, existing_nos):
    """快照里有地址、且库里存在该订单 → [(订单号, 整段地址)]。

    ⚠️ 地址**不裁剪**：`Address` 里省市县常常和详细地址重复（旧站的拼接方式），
    整段写进去虽然啰嗦，但绝不会丢信息。裁错了就把省市弄没了。
    """
    out = []
    for o in orders:
        no = s(o.get('OrderID'))
        addr = s(o.get('Address'))
        if no in existing_nos and addr:
            out.append((no, addr))
    return out


def delivery_updates(orders, existing_nos):
    """快照里有发货时间、且库里存在该订单 → [(订单号, 'YYYY-MM-DD HH:MM:SS')]。"""
    out = []
    for o in orders:
        no = s(o.get('OrderID'))
        dt = s(o.get('DeliveryTime'))
        if no in existing_nos and dt:
            out.append((no, dt))
    return out


def logistics_updates(orders, existing_nos):
    """快照里有快递公司、且库里存在该订单 → [(订单号, 快递公司名)]。

    **只回名字不回 id** —— 公司 id 要等插入之后才知道，解析放在写库那一步。
    """
    out = []
    for o in orders:
        no = s(o.get('OrderID'))
        name = s(o.get('LogisticsName'))
        if no in existing_nos and name:
            out.append((no, name))
    return out


def missing_orders(orders, existing_nos):
    """快照里有、库里没有的订单。**应当正好 2 张**，多一张少一张都要停下看。"""
    return [o for o in orders if s(o.get('OrderID')) not in existing_nos]


def products_by_legacy_id():
    """旧 ProductID → products.json 的序号（写订单明细要按序号找新 spu_id）。"""
    with open(SNAP / 'products.json', encoding='utf-8') as f:
        prods = json.load(f)
    return {s(p.get('ProductID')): i for i, p in enumerate(prods)}


def build_missing(orders_missing, prod_index, member_ids):
    """把 2 张漏网订单转成库内行。

    返回 (orders, items)。金额口径与 09-29 那次导入保持一致（见 import_legacy_mall.build_orders）：
    应付 = 原价 − 优惠 + 运费 + 调价；这里运费无数据记 0。
    """
    out_o, out_i = [], []
    for o in orders_missing:
        no = s(o.get('OrderID'))
        legacy_uid = s(o.get('UserID'))
        if legacy_uid not in member_ids:
            raise SystemExit('✗ 订单 %s 的会员 %s 不在库内，无法插入' % (no, legacy_uid))

        total_fen = yuan_to_fen(o.get('Money'))
        paid_raw = s(o.get('ModifiedMoney'))
        pay_fen = yuan_to_fen(paid_raw) if paid_raw else total_fen
        goods = o.get('Goods') or []

        out_o.append({
            'no': no,
            'user_legacy_id': legacy_uid,
            'status': MISSING_ORDER_STATUS,
            'refund_status': 0,
            'pay_status': 1,                       # 都带 PayTime
            'pay_time': s(o.get('PayTime')) or None,
            'create_time': s(o.get('CrTime')),
            'finish_time': s(o.get('ConfirmTime')) or None,
            'delivery_time': s(o.get('DeliveryTime')) or None,
            'total_price': total_fen,
            'discount_price': max(0, total_fen - pay_fen),
            'adjust_price': max(0, pay_fen - total_fen),
            'pay_price': pay_fen,
            'product_count': sum(int(s(g.get('Amount')) or 0) for g in goods),
            'delivery_type': 1,
            'terminal': 0,
            'logistics_no': s(o.get('InvoiceNo')) or None,
            'logistics_name': s(o.get('LogisticsName')),
            'receiver_name': s(o.get('Contact')),
            'receiver_mobile': s(o.get('Mobile')),
            'receiver_detail_address': s(o.get('Address')) or None,
            'remark': '迁移自旧商城（2026-10-08 补录）',
        })

        for g in goods:
            pid = s(g.get('ProductID'))
            idx = prod_index.get(pid)
            count = int(s(g.get('Amount')) or 0)
            unit = yuan_to_fen(g.get('Price'))
            out_i.append({
                'order_no': no,
                'user_legacy_id': legacy_uid,
                'product_idx': idx,
                'spu_name': s(g.get('ProductName')),
                'price': unit,
                'count': count,
                'pay_price': unit * count,
            })
    return out_o, out_i


# ══════════════════════════════════════════════════════════════════════════════
# 数据库层
# ══════════════════════════════════════════════════════════════════════════════

def connect():
    env = _env()
    try:
        conn = pymysql.connect(host='8.146.239.137', port=3306, user='root',
                               password=env['MYSQL_DB_SERVER_PASSWRD'],
                               database='ruoyi-vue-pro', charset='utf8mb4',
                               cursorclass=pymysql.cursors.DictCursor, autocommit=False)
        conn.cursor().execute('SELECT 1')
        return conn
    except Exception:
        pass
    tunnel = DBTunnel()
    host, port = tunnel.__enter__()
    conn = pymysql.connect(host=host, port=port, user='root',
                           password=env['MYSQL_DB_SERVER_PASSWRD'],
                           database='ruoyi-vue-pro', charset='utf8mb4',
                           cursorclass=pymysql.cursors.DictCursor, autocommit=False)
    conn._tunnel = tunnel          # 挂在连接上，close 时一起关
    return conn


def order_nos(conn):
    with conn.cursor() as cur:
        cur.execute('SELECT no FROM trade_order WHERE tenant_id=%s', (TENANT_ID,))
        return {r['no'] for r in cur.fetchall()}


def member_id_map(conn):
    """旧会员编号 → 新 member_user.id。

    库里没有旧的 legacy_id 列，用**手机号**回推：快照 members.json 的 Mobile ↔ 库内 mobile。
    """
    with open(SNAP / 'members.json', encoding='utf-8') as f:
        members = json.load(f)
    with conn.cursor() as cur:
        cur.execute('SELECT id, mobile FROM member_user WHERE tenant_id=%s', (TENANT_ID,))
        by_mobile = {s(r['mobile']): r['id'] for r in cur.fetchall()}
    out = {}
    for m in members:
        mob = s(m.get('Mobile'))
        if mob in by_mobile:
            out[s(m.get('UserID'))] = by_mobile[mob]
    return out


def spu_sku_map(conn):
    """products.json 序号 → (spu_id, sku_id)。序号↔新 id 的对应取自 09-29 的导入清单。"""
    with open(ROOT / 'scripts' / 'import-legacy-mall-manifest.json', encoding='utf-8') as f:
        man = json.load(f)
    idx_to_spu = {e['idx']: e['id'] for e in man['created']['product_spu']}
    with conn.cursor() as cur:
        cur.execute('SELECT id, spu_id FROM product_sku WHERE tenant_id=%s', (TENANT_ID,))
        sku_of_spu = {}
        for r in cur.fetchall():
            sku_of_spu.setdefault(r['spu_id'], r['id'])
    return idx_to_spu, sku_of_spu


def backup(conn, address_up, delivery_up, logistics_up, missing, added_express):
    """把即将被改动的行 dump 成 TSV + rollback.sql。返回 (tsv 路径, sql 路径)。"""
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime('%Y%m%d-%H%M%S')
    tsv = BACKUP_DIR / ('backfill_before_%s.tsv' % stamp)
    sql = BACKUP_DIR / ('backfill_rollback_%s.sql' % stamp)

    nos = [n for n, _ in address_up] + [n for n, _ in delivery_up] + [n for n, _ in logistics_up]
    with conn.cursor() as cur:
        cur.execute(
            'SELECT no, receiver_detail_address, delivery_time, logistics_id, '
            'receiver_area_id FROM trade_order WHERE tenant_id=%s', (TENANT_ID,))
        rows = [r for r in cur.fetchall() if r['no'] in set(nos)]

    with open(tsv, 'w', encoding='utf-8', newline='') as f:
        f.write('no\treceiver_detail_address\tdelivery_time\tlogistics_id\treceiver_area_id\n')
        for r in rows:
            f.write('\t'.join('' if r[c] is None else str(r[c]) for c in
                              ('no', 'receiver_detail_address', 'delivery_time',
                               'logistics_id', 'receiver_area_id')) + '\n')

    def lit(v):
        return 'NULL' if v is None or v == '' else "'%s'" % str(v).replace("'", "''")

    lines = ['-- 回填的回滚脚本（%s）' % stamp,
             '-- 订单字段复原', 'START TRANSACTION;']
    for r in rows:
        lines.append(
            "UPDATE trade_order SET receiver_detail_address=%s, delivery_time=%s, "
            "logistics_id=%s WHERE tenant_id=%d AND no=%s;" % (
                lit(r['receiver_detail_address']), lit(r['delivery_time']),
                lit(r['logistics_id']), TENANT_ID, lit(r['no'])))
    if missing:
        lines.append('-- 删掉本次补录的订单与明细')
        lines.append('DELETE FROM trade_order_item WHERE tenant_id=%d AND order_id IN '
                     '(SELECT id FROM trade_order WHERE tenant_id=%d AND no IN (%s));'
                     % (TENANT_ID, TENANT_ID,
                        ','.join(lit(s(o['OrderID'])) for o in missing)))
        lines.append('DELETE FROM trade_order WHERE tenant_id=%d AND no IN (%s);'
                     % (TENANT_ID, ','.join(lit(s(o['OrderID'])) for o in missing)))
    # ⚠️ 只删**本次真插入**的那几家。若写死成全部 13 家，将来重跑（有几家已存在、
    # 被跳过）时回滚会把本来就在的公司一起删掉。
    if added_express:
        lines.append('-- 删掉本次新增的快递公司（只有这几家是本次插的）')
        lines.append('DELETE FROM trade_delivery_express WHERE tenant_id=%d AND name IN (%s);'
                     % (TENANT_ID, ','.join(lit(n) for n in added_express)))
    lines.append('COMMIT;')

    sql.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    return tsv, sql


def apply(conn, plan, name_to_id):
    """执行三段 UPDATE。`logistics` 里存的是公司名，这里才解析成 id。"""
    cur = conn.cursor()
    n1 = n2 = n3 = 0
    for no, addr in plan['address']:
        cur.execute('UPDATE trade_order SET receiver_detail_address=%s '
                    'WHERE tenant_id=%s AND no=%s', (addr, TENANT_ID, no))
        n1 += cur.rowcount
    for no, dt in plan['delivery']:
        cur.execute('UPDATE trade_order SET delivery_time=%s '
                    'WHERE tenant_id=%s AND no=%s', (dt, TENANT_ID, no))
        n2 += cur.rowcount
    for no, name in plan['logistics']:
        eid = name_to_id.get(name)
        if eid is None:
            continue
        cur.execute('UPDATE trade_order SET logistics_id=%s '
                    'WHERE tenant_id=%s AND no=%s', (eid, TENANT_ID, no))
        n3 += cur.rowcount
    return n1, n2, n3


def insert_express(conn, rows):
    sql = ('INSERT INTO trade_delivery_express '
           '(code,name,logo,sort,status,creator,create_time,updater,update_time,deleted,tenant_id) '
           'VALUES (%s,%s,NULL,%s,%s,%s,NOW(),%s,NOW(),0,%s)')
    ids = {}
    cur = conn.cursor()
    for r in rows:
        cur.execute(sql, (r['code'], r['name'], r['sort'], r['status'], '',
                          '', TENANT_ID))
        ids[r['name']] = cur.lastrowid
    return ids


def insert_missing(conn, orders, items, member_ids):
    idx_to_spu, sku_of_spu = spu_sku_map(conn)
    order_sql = 'INSERT INTO trade_order (%s) VALUES (%s)' % (
        ','.join('`%s`' % c for c in ORDER_COLS), ','.join(['%s'] * len(ORDER_COLS)))
    item_sql = 'INSERT INTO trade_order_item (%s) VALUES (%s)' % (
        ','.join('`%s`' % c for c in ITEM_COLS), ','.join(['%s'] * len(ITEM_COLS)))

    cur = conn.cursor()
    made = []
    for o in orders:
        uid = member_ids[o['user_legacy_id']]
        cur.execute(order_sql, (
            o['no'], 0, o['terminal'], uid, '', o['status'], o['product_count'],
            o['remark'], 0, None, None, o['pay_status'],
            o['pay_time'], None, o['finish_time'], None, o['total_price'],
            o['discount_price'], 0, o['adjust_price'], o['pay_price'],
            o['delivery_type'], None, o['logistics_no'], o['delivery_time'],
            None, o['receiver_name'], o['receiver_mobile'], None,
            o['receiver_detail_address'], o['refund_status'], 0, None,
            0, 0, 0, 0, 0, 0, '', o['create_time'], '', o['create_time'],
            0, TENANT_ID))
        order_id = cur.lastrowid
        made.append((o['no'], order_id))
        for it in items:
            if it['order_no'] != o['no']:
                continue
            spu_id = idx_to_spu.get(it['product_idx'], 0) if it['product_idx'] is not None else 0
            sku_id = sku_of_spu.get(spu_id, 0)
            cur.execute(item_sql, (
                uid, order_id, None, spu_id, it['spu_name'], sku_id,
                DEFAULT_PROPERTIES, None, it['count'], 0, it['price'],
                0, 0, 0, it['pay_price'], 0, 0, 0, 0, 0, None, 0, '', o['create_time'],
                '', o['create_time'], 0, TENANT_ID))
    return made


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true', help='真写库（默认只 dry-run）')
    args = ap.parse_args()

    orders = load_orders()
    rows_express = express_rows()

    conn = connect()
    try:
        exist = order_nos(conn)
        miss = missing_orders(orders, exist)
        addr = address_updates(orders, exist)
        dele = delivery_updates(orders, exist)
        member_ids = member_id_map(conn)
        prod_index = products_by_legacy_id()
        m_orders, m_items = build_missing(miss, prod_index, member_ids)

        with conn.cursor() as cur:
            cur.execute('SELECT name, id FROM trade_delivery_express WHERE tenant_id=%s',
                        (TENANT_ID,))
            existing_expr = {r['name']: r['id'] for r in cur.fetchall()}

        # 补录的 2 单也要带上物流，所以它们算进「库内有」的集合里
        after = exist | {o['no'] for o in m_orders}
        log_up = logistics_updates(orders, after)
        to_add = [r for r in rows_express if r['name'] not in existing_expr]
        added_names = [r['name'] for r in to_add]

        print('=' * 68)
        print('租户 %d 回填计划' % TENANT_ID)
        print('=' * 68)
        print('库内订单 %d 张 · 快照 %d 张' % (len(exist), len(orders)))
        print()
        print('① 收货地址      将更新 %d 张' % len(addr))
        print('② 发货时间      将更新 %d 张' % len(dele))
        print('③ 物流公司      将更新 %d 张' % len(log_up))
        print('④ 快递公司      库内已有 %d 家，待新增 %d 家'
              % (len(existing_expr), len([r for r in rows_express
                                          if r['name'] not in existing_expr])))
        print('⑤ 漏网订单      待补 %d 张 / %d 行明细' % (len(m_orders), len(m_items)))
        print()
        if len(miss) != 2:
            print('⚠️  预期漏网订单正好 2 张，实际 %d 张 —— 先查清楚再往下' % len(miss))
        print('待新增的快递公司：')
        for r in rows_express:
            mark = '（已存在，跳过）' if r['name'] in existing_expr else ''
            code = r['code'] or '← 编码留空'
            print('   %-8s %-8s %s' % (r['name'], code, mark))
        print()
        print('漏网订单：')
        # ⚠️ 这里要打**快照原始行**（`miss`）。`m_orders` 已被 build_missing 换成库内结构，
        # 字段名是 Money→total_price、Contact→receiver_name，拿它取 'Money' 会全是 None。
        for o in miss:
            print('   %s  %s 元  收件人=%s  状态→%d  %s'
                  % (o.get('OrderID'), o.get('Money'), o.get('Contact'),
                     MISSING_ORDER_STATUS, o.get('CrTime')))

        if not args.apply:
            print()
            print('（dry-run，未写库。加 --apply 执行）')
            return

        tsv, sql = backup(conn, addr, dele, log_up, miss, added_names)
        print()
        print('已备份：', tsv)
        print('回滚脚本：', sql)

        # 顺序要紧：先把快递公司和 2 张订单**插进去**，再跑三段 UPDATE，
        # 否则新补的那 2 单会漏掉 logistics_id。
        ids = dict(existing_expr)
        if to_add:
            ids.update(insert_express(conn, to_add))
        made = insert_missing(conn, m_orders, m_items, member_ids)

        n1, n2, n3 = apply(conn, {'address': addr, 'delivery': dele, 'logistics': log_up}, ids)
        conn.commit()
        print()
        print('✓ 地址 %d 行 · 发货时间 %d 行 · 物流 %d 行 · 新快递公司 %d 家 · 新订单 %s'
              % (n1, n2, n3, len(to_add), [m[0] for m in made]))
    except Exception:
        conn.rollback()
        raise
    finally:
        tunnel = getattr(conn, '_tunnel', None)
        conn.close()
        if tunnel:
            tunnel.__exit__(None, None, None)


if __name__ == '__main__':
    main()
