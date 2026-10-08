"""核对 oldMallData/ 快照 vs 线上库租户 162 的实际条数（只读 SELECT COUNT）。

公网 3306 不通、MySQL 授权按来源主机限制，所以走 `_dbtunnel.py`（本机 -> 应用机 -> 数据库机）,
用 .env 里应用自己那套凭据连。

用法：python scripts/check_import_status.py
"""
import json
import sys
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _dbtunnel import DBTunnel, _env  # noqa: E402

TENANT = 162
DB_NAME = 'ruoyi-vue-pro'
ROOT = Path(__file__).resolve().parent.parent
SNAPSHOT = ROOT / 'oldMallData' / 'data'

CHECKS = [
    ('categories.json',      'product_category'),
    ('products.json',        'product_spu'),
    ('members.json',         'member_user'),
    ('orders.json',          'trade_order'),
    ('order_items.json',     'trade_order_item'),
    ('articles.json',        'promotion_article'),
    ('article_classes.json', 'promotion_article_category'),
    ('coupons.json',         'promotion_coupon'),
]


def snap(fname):
    with open(SNAPSHOT / fname, encoding='utf-8') as f:
        return json.load(f)


def scalar(cur, query, args=()):
    cur.execute(query, args)
    row = cur.fetchone()
    return list(row.values())[0] if isinstance(row, dict) else row[0]


def main():
    env = _env()
    with DBTunnel() as (host, port):
        conn = pymysql.connect(host=host, port=port, user='root',
                               password=env['MYSQL_DB_SERVER_PASSWRD'],
                               database=DB_NAME, charset='utf8mb4',
                               cursorclass=pymysql.cursors.DictCursor)
        cur = conn.cursor()

        cur.execute('SHOW TABLES')
        tables = {list(r.values())[0] for r in cur.fetchall()}

        print('=== oldMallData 快照 vs 线上库租户 162 ===')
        print(f"{'快照文件':26}{'快照':>7}{'库内(未删)':>11}   表")
        for fname, table in CHECKS:
            n = len(snap(fname))
            if table not in tables:
                print(f'{fname:26}{n:>7}{"—":>11}   {table}（表不存在）')
                continue
            cur.execute(f'SHOW COLUMNS FROM `{table}`')
            cols = {list(r.values())[0] for r in cur.fetchall()}
            live = scalar(cur, f'SELECT COUNT(*) FROM `{table}` '
                               f'WHERE tenant_id=%s AND deleted=0', (TENANT,))
            flag = '' if live == n else '   <<< 差 %+d' % (live - n)
            print(f'{fname:26}{n:>7}{live:>11}   {table}{flag}')

        print()
        print('=== 商品：主图 / 详情 / 轮播图 填充率（未删）===')
        print('  商品总数            ', scalar(cur, 'SELECT COUNT(*) FROM product_spu '
                                                'WHERE tenant_id=%s AND deleted=0', (TENANT,)))
        print('  主图是 OSS 地址    ', scalar(cur,
              "SELECT COUNT(*) FROM product_spu WHERE tenant_id=%s AND deleted=0 "
              "AND pic_url LIKE 'http%%'", (TENANT,)))
        print('  主图仍是 logo 占位 ', scalar(cur,
              "SELECT COUNT(*) FROM product_spu WHERE tenant_id=%s AND deleted=0 "
              "AND pic_url LIKE '%%assets/logo.png%%'", (TENANT,)))
        print('  有详情描述         ', scalar(cur,
              "SELECT COUNT(*) FROM product_spu WHERE tenant_id=%s AND deleted=0 "
              "AND description IS NOT NULL AND description<>''", (TENANT,)))

        print()
        print('=== 订单：收货信息（未删）===')
        print('  订单总数            ', scalar(cur, 'SELECT COUNT(*) FROM trade_order '
                                                'WHERE tenant_id=%s AND deleted=0', (TENANT,)))
        print('  有收货人           ', scalar(cur,
              "SELECT COUNT(*) FROM trade_order WHERE tenant_id=%s AND deleted=0 "
              "AND receiver_name IS NOT NULL AND receiver_name<>''", (TENANT,)))
        print('  有发货时间         ', scalar(cur,
              "SELECT COUNT(*) FROM trade_order WHERE tenant_id=%s AND deleted=0 "
              "AND delivery_time IS NOT NULL", (TENANT,)))
        print('  有物流公司         ', scalar(cur,
              "SELECT COUNT(*) FROM trade_order WHERE tenant_id=%s AND deleted=0 "
              "AND logistics_no IS NOT NULL AND logistics_no<>''", (TENANT,)))

        print()
        print('=== 那 2 单到底在不在（按旧订单号）===')
        for no in ('202609291643048054786', '202609291519523241867'):
            cur.execute('SELECT COUNT(*) AS c FROM trade_order WHERE tenant_id=%s AND no=%s',
                        (TENANT, no))
            print(f'  {no}  命中 {cur.fetchone()["c"]}')

        print()
        print('=== 会员 ===')
        print('  会员总数（未删）    ', scalar(cur, 'SELECT COUNT(*) FROM member_user '
                                                'WHERE tenant_id=%s AND deleted=0', (TENANT,)))
        print('  无手机号的          ', scalar(cur,
              "SELECT COUNT(*) FROM member_user WHERE tenant_id=%s AND deleted=0 "
              "AND (mobile IS NULL OR mobile='')", (TENANT,)))

        cur.close()
        conn.close()


if __name__ == '__main__':
    main()
