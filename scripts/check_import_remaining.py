"""把 oldMallData 里还没核过的那些文件，逐个找库内对应表并查条数（只读）。

只在 select_import_status.py 已确认的几项之外，补齐「其余快照有没有落库」。
"""
import json
import sys
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _dbtunnel import DBTunnel, _env  # noqa: E402

TENANT = 162
ROOT = Path(__file__).resolve().parent.parent
SNAP = ROOT / 'oldMallData' / 'data'

# 快照文件 -> 候选表名（都按 tenant_id 筛；表不存在会标出来）
CANDIDATES = [
    ('member_levels.json',        ['member_level']),
    ('member_reg_items.json',     ['member_config', 'member_register_config']),
    ('logistics_companies.json',  ['trade_delivery_express']),
    ('freight_templates.json',    ['trade_delivery_express_template']),
    ('articles.json',             ['promotion_article']),
    ('article_classes.json',      ['promotion_article_category']),
    ('coupons.json',              ['promotion_coupon']),
    ('form_defs.json',            []),
    ('form_rows.json',            []),
    ('seo_settings.json',         []),
    ('friendly_urls.json',        []),
    ('site_rewrite.json',         []),
    ('site_pages.json',           []),
    ('admins.json',               ['system_users']),
    ('downloads.json',            ['infra_file']),
    ('site_gallery.json',         []),
    ('shop_config.json',          []),
    ('site.json',                 []),
    ('site_config.json',          []),
    ('site_settings.json',        []),
    ('finance_records.json',      []),
]


def n_snap(name):
    p = SNAP / name
    if not p.exists():
        return None
    with open(p, encoding='utf-8') as f:
        return len(json.load(f))


def main():
    env = _env()
    with DBTunnel() as (host, port):
        conn = pymysql.connect(host=host, port=port, user='root',
                               password=env['MYSQL_DB_SERVER_PASSWRD'],
                               database='ruoyi-vue-pro', charset='utf8mb4',
                               cursorclass=pymysql.cursors.DictCursor)
        cur = conn.cursor()
        cur.execute('SHOW TABLES')
        tables = {list(r.values())[0] for r in cur.fetchall()}

        print(f"{'快照文件':26}{'条数':>6}   库内对应表")
        for fname, cands in CANDIDATES:
            n = n_snap(fname)
            if n is None:
                continue
            if not cands:
                print(f'{fname:26}{n:>6}   —— 库里没有对应表（无导入目标）')
                continue
            for t in cands:
                if t not in tables:
                    print(f'{fname:26}{n:>6}   {t}（表不存在）')
                    continue
                cur.execute('SHOW COLUMNS FROM `%s`' % t)
                cols = {c['Field'] for c in cur.fetchall()}
                w, a = [], []
                if 'tenant_id' in cols:
                    w.append('tenant_id=%s')
                    a.append(TENANT)
                if 'deleted' in cols:
                    w.append('deleted=0')
                sql = 'SELECT COUNT(*) AS c FROM `%s`' % t
                if w:
                    sql += ' WHERE ' + ' AND '.join(w)
                cur.execute(sql, a)
                got = cur.fetchone()['c']
                flag = '  OK' if got == n else '  <<< 差 %+d' % (got - n)
                print(f'{fname:26}{n:>6}   {t} = {got}{flag}')

        cur.close()
        conn.close()


if __name__ == '__main__':
    main()
