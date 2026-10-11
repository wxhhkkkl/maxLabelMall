"""执行 003 的建表迁移：`sql/mysql/003-product-filter-dimension.sql`。

为什么要一个脚本、而不是手工贴一次 SQL：

  · **可重复**。脚本是幂等的（DDL 全是 `CREATE TABLE IF NOT EXISTS`），
    误跑第二次不会出错，换环境也能照跑一遍。
  · **有校验**。执行完立刻回查三张表是否真的建出来了、列是否对得上，
    而不是"命令没报错就算成功"。
  · **留痕**。谁在什么时候往生产库里加了什么，能在仓库里查到。

连库方式沿用 `scripts/_dbtunnel.py`：先试直连数据库机，不通就走
「本机 → 应用机(SSH) → 数据库机内网 3306」的隧道（MySQL 授权是按来源主机给的，
只有从应用机发起才匹配得上）。

用法：
    python scripts/apply_003_filter_dimension.py                        # 先看现状，再执行，再校验
    python scripts/apply_003_filter_dimension.py --dry-run              # 只看现状与将要执行的 SQL
    python scripts/apply_003_filter_dimension.py --file 004-xxx.sql     # 跑别的迁移文件
                                                                        #（不给 --expect-tables 时就只报告影响行数）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import pymysql

from _dbtunnel import DBTunnel

ROOT = Path(__file__).resolve().parent.parent
SQL_PATH = ROOT / 'yudao-cloud' / 'sql' / 'mysql' / '003-product-filter-dimension.sql'
DATABASE = 'ruoyi-vue-pro'

# 这三张表是本特性的全部新增；少一张，后台保存维度就直接报错
EXPECTED_TABLES = ['product_filter_dimension', 'product_filter_value', 'product_spu_filter_value']

# 每张表**必须**有的列。写死在这里是刻意的：它让"表建出来了但列不对"这种情况也能被发现，
# 而不是只检查表名存在。
EXPECTED_COLUMNS = {
    'product_filter_dimension': ['id', 'category_id', 'name', 'sort', 'remark', 'deleted', 'tenant_id'],
    'product_filter_value': ['id', 'dimension_id', 'name', 'sort', 'remark', 'deleted', 'tenant_id'],
    'product_spu_filter_value': ['id', 'spu_id', 'filter_value_id', 'deleted', 'tenant_id'],
}


def _env():
    out = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1)
            out[k.strip()] = v.strip().strip('"').strip("'")
    return out


def connect():
    env = _env()
    try:
        conn = pymysql.connect(host='8.146.239.137', port=3306, user='root',
                               password=env['MYSQL_DB_SERVER_PASSWRD'],
                               database=DATABASE, charset='utf8mb4', autocommit=False)
        conn.cursor().execute('SELECT 1')
        print('连接方式：直连数据库机')
        return conn
    except Exception:
        pass
    tunnel = DBTunnel()
    host, port = tunnel.__enter__()
    conn = pymysql.connect(host=host, port=port, user='root',
                           password=env['MYSQL_DB_SERVER_PASSWRD'],
                           database=DATABASE, charset='utf8mb4', autocommit=False)
    conn._tunnel = tunnel
    print('连接方式：经应用机 SSH 隧道')
    return conn


def split_statements(sql_text: str):
    """按分号切 SQL 语句，先剥掉 `--` 行注释。

    这个迁移文件里每条语句都很简单（单行注释 + CREATE TABLE），
    所以不需要 SQL 解析器；但**必须先剥注释**，否则注释里的分号会把语句切碎。
    """
    lines = []
    for line in sql_text.splitlines():
        stripped = line.strip()
        if stripped.startswith('--'):
            continue
        lines.append(line)
    text = '\n'.join(lines)
    return [s.strip() for s in text.split(';') if s.strip()]


def _query_table_names(conn, tables):
    """回查三张表在不在。

    ⚠️ 这里**必须**把占位符拼进 SQL 文本，再把表名逐个作为参数传进去。
    写成 `IN (%s)` 再传一个 `','.join(['%s'] * 3)` 是错的 —— pymysql 会把那串
    当成**一个字符串字面量**转义，SQL 于是变成 `table_name IN ('%s,%s,%s')`，
    拿列去比一个字面量，**永远返回空**。不报错，只是悄悄给出错误答案。
    （这个坑我踩过一次：第一次执行时它谎报"执行前一张表都没有"、"执行后一张都没建出来"。）
    """
    placeholders = ', '.join(['%s'] * len(tables))
    with conn.cursor() as cur:
        cur.execute(
            f'SELECT table_name FROM information_schema.tables '
            f'WHERE table_schema = %s AND table_name IN ({placeholders})',
            (DATABASE, *tables),
        )
        return sorted(r[0] for r in cur.fetchall())


def columns_of(conn, table):
    with conn.cursor() as cur:
        cur.execute(
            'SELECT column_name FROM information_schema.columns '
            'WHERE table_schema = %s AND table_name = %s',
            (DATABASE, table),
        )
        return sorted(r[0] for r in cur.fetchall())


def verify(conn, expect_tables):
    """执行后的校验。**不靠"命令没报错"当成功** —— 回查表与列。"""
    problems = []
    present = set(_query_table_names(conn, expect_tables))
    for table in expect_tables:
        if table not in present:
            problems.append(f'  表缺失：{table}')
            continue
        cols = set(columns_of(conn, table))
        missing = [c for c in EXPECTED_COLUMNS.get(table, []) if c not in cols]
        if missing:
            problems.append(f'  {table} 缺列：{", ".join(missing)}')
        else:
            print(f'  ✓ {table}（{len(cols)} 列，关键列齐全）')
    return problems


def main():
    dry_run = '--dry-run' in sys.argv
    # 允许跑任意迁移文件；不给 --expect-tables 就只报告影响行数（适合 INSERT 型的迁移）
    sql_path = SQL_PATH
    if '--file' in sys.argv:
        sql_path = ROOT / 'yudao-cloud' / 'sql' / 'mysql' / sys.argv[sys.argv.index('--file') + 1]
    # 跑的是别的迁移文件时，默认**不**去校验那三张表 —— 那是 003 专属的检查项。
    # 数据类迁移（INSERT 等）的意义在"影响几行"，脚本会为每条语句报出来。
    expect_tables = EXPECTED_TABLES if sql_path == SQL_PATH else []
    if '--expect-tables' in sys.argv:
        expect_tables = sys.argv[sys.argv.index('--expect-tables') + 1].split(',')

    sql_text = sql_path.read_text(encoding='utf-8')
    statements = split_statements(sql_text)
    print(f'迁移脚本：{sql_path.relative_to(ROOT)}')
    print(f'语句数：{len(statements)}')

    conn = connect()
    try:
        if expect_tables:
            before = _query_table_names(conn, expect_tables)
            print(f'\n执行前已存在的表：{before or "（无）"}')
        else:
            print('\n（数据类迁移，没有"执行前已存在的表"可报；下面会逐条打印影响行数）')

        if dry_run:
            print('\n--dry-run：不执行。将按顺序执行上面那些语句。')
            return

        with conn.cursor() as cur:
            for statement in statements:
                cur.execute(statement)
        conn.commit()
        print('\n执行完成，已提交。')

        print('\n校验：')
        problems = verify(conn, expect_tables)
        if problems:
            print('\n'.join(problems))
            print('\n⚠️ 校验未通过 —— 请人工介入，不要当成成功。')
            sys.exit(1)
        print('\n✅ 三张表都已建好，关键列齐全。')
    finally:
        conn.close()
        tunnel = getattr(conn, '_tunnel', None)
        if tunnel is not None:
            tunnel.__exit__(None, None, None)


if __name__ == '__main__':
    main()
