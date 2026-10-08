"""追查 check_import_status.py 里三处对不上的地方（只读）。

① 会员：库内 93 > 快照 91 —— 多出来的是谁？
② 发货时间：库内 0 条，快照里到底有没有？
③ 收货地址：库内 393/393 有，但 09-29 报告说「无收货地址」—— 是谁补的？
"""
import json
import sys
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _dbtunnel import DBTunnel, _env  # noqa: E402

TENANT = 162
ROOT = Path(__file__).resolve().parent.parent


def snap(name):
    with open(ROOT / 'oldMallData' / 'data' / name, encoding='utf-8') as f:
        return json.load(f)


def mask(s):
    """手机号只留首尾，避免把个人信息打进日志"""
    s = str(s or '')
    return s[:3] + '*' * max(0, len(s) - 7) + s[-4:] if len(s) >= 7 else ('*' * len(s))


def main():
    env = _env()
    members = snap('members.json')
    orders = snap('orders.json')

    # ---- 快照侧的统计 ----
    snap_mobiles = {str(m.get('Mobile') or m.get('Tel') or '').strip() for m in members}
    snap_mobiles.discard('')
    print('=== 快照侧 ===')
    print('  members.json 条数            ', len(members))
    print('  其中有手机号                 ', len(snap_mobiles))
    print('  订单有 DeliveryTime 的        ',
          sum(1 for o in orders if str(o.get('DeliveryTime') or '').strip(' 0:-')))
    print('  订单有 LogisticsName 的       ',
          sum(1 for o in orders if str(o.get('LogisticsName') or '').strip()))
    print('  订单有 Address 的             ',
          sum(1 for o in orders if str(o.get('Address') or '').strip()))

    with DBTunnel() as (host, port):
        conn = pymysql.connect(host=host, port=port, user='root',
                               password=env['MYSQL_DB_SERVER_PASSWRD'],
                               database='ruoyi-vue-pro', charset='utf8mb4',
                               cursorclass=pymysql.cursors.DictCursor)
        cur = conn.cursor()

        # ---- ① 会员：库里比快照多谁 ----
        cur.execute('SELECT mobile, nickname, create_time, deleted FROM member_user '
                    'WHERE tenant_id=%s', (TENANT,))
        rows = cur.fetchall()
        db_mobiles = {str(r['mobile'] or '').strip() for r in rows}
        db_mobiles.discard('')
        extra = db_mobiles - snap_mobiles
        missing = snap_mobiles - db_mobiles
        print()
        print('=== ① 会员对账 ===')
        print('  库内会员行数（含已删）        ', len(rows))
        print('  库内有手机号的                ', len(db_mobiles))
        print('  快照有、库里没有的（漏导）     ', len(missing))
        print('  库里有、快照没有的（多出来）   ', len(extra))
        for r in rows:
            mob = str(r['mobile'] or '').strip()
            if mob in extra:
                print(f'    多出：{mask(mob)}  昵称={r["nickname"]!r}  建号={r["create_time"]}  deleted={r["deleted"]}')

        # ---- ② 发货时间 ----
        cur.execute('SHOW COLUMNS FROM trade_order')
        cols = {c['Field'] for c in cur.fetchall()}
        print()
        print('=== ② 发货时间 ===')
        # ⚠️ delivery_time / receive_time 是 DATETIME，别用 `<>""` 比（会报 1525）
        for col, expr in (('delivery_time', 'delivery_time IS NOT NULL'),
                          ('receive_time', 'receive_time IS NOT NULL'),
                          ('logistics_id', 'logistics_id IS NOT NULL AND logistics_id>0'),
                          ('logistics_no', 'logistics_no IS NOT NULL AND logistics_no<>""')):
            if col not in cols:
                print(f'  trade_order 没有 {col} 列')
                continue
            cur.execute(f'SELECT COUNT(*) AS c FROM trade_order WHERE tenant_id=%s '
                        f'AND deleted=0 AND {expr}', (TENANT,))
            print(f'  {col:16} 非空的 {cur.fetchone()["c"]}')

        # ---- ③ 收货地址：取一单比对 ----
        print()
        print('=== ③ 收货地址抽样比对（快照 vs 库内）===')
        by_no = {str(o['OrderID']): o for o in orders}
        cur.execute('SELECT no, receiver_name, receiver_mobile, receiver_area_id, '
                    'receiver_detail_address, logistics_no, status, pay_status FROM trade_order '
                    'WHERE tenant_id=%s AND deleted=0 ORDER BY id LIMIT 3', (TENANT,))
        for r in cur.fetchall():
            o = by_no.get(str(r['no']), {})
            print(f'  订单 {r["no"]}  状态={r["status"]}/支付={r["pay_status"]} '
                  f'物流单号={r["logistics_no"]!r}')
            print(f'    库内：{r["receiver_name"]} {mask(r["receiver_mobile"])} '
                  f'区划={r["receiver_area_id"]} {(r["receiver_detail_address"] or "")}'[:110])
            print(f'    快照：{o.get("Contact")} {mask(o.get("Mobile"))} '
                  f'{(o.get("Address") or "")}'[:110])

        cur.close()
        conn.close()


if __name__ == '__main__':
    main()
