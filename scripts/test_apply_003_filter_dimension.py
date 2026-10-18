"""`apply_003_filter_dimension.py`（现在也是**通用迁移执行器**）的纯函数层单测。

连库、执行 DDL、回查列那些步骤**不在这里假装覆盖** —— 它们只能靠一次真实的 dry-run
加一次真跑、并用 `information_schema` 回查（照 `test_deploy.py` 的口径）。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import apply_003_filter_dimension as ap  # noqa: E402


class 碰就炸的连接:
    """任何访问都直接失败 —— 用来证明 `verify` 根本没往连接上走。"""

    def cursor(self):
        raise AssertionError('不该碰连接：没有期望的表时，verify 应当直接返回')

    def __getattr__(self, item):
        raise AssertionError(f'不该碰连接：verify 动了 {item}')


def 记事的假连接(问过的):
    """按 SQL 内容分别回答"有哪些表"和"有哪些列"，并记录被问过什么。"""

    class 假游标:
        def __init__(self):
            self._rows = []

        def execute(self, sql, params):
            if 'information_schema.tables' in sql:
                问过的.append(params)
                self._rows = [('product_filter_dimension',)]
            else:
                self._rows = [(c,) for c in ap.EXPECTED_COLUMNS[params[1]]]

        def fetchall(self):
            return self._rows

        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

    class 假连接:
        def cursor(self):
            return 假游标()

    return 假连接()


# ── 没有期望的表 → 不校验（2026-10-16 修的 bug） ─────────────────────────────

def test_没有期望的表时_verify_直接返回且不碰连接():
    """⚠️ 这是 2026-10-16 真实踩过的坑。

    `--file 010-invoice-title-kind.sql`（不给 `--expect-tables`）时 `expect_tables` 是空列表，
    而 `verify` 会把它原样喂给 `_query_table_names` —— 那里拼的是
    `table_name IN ({placeholders})`，空列表就拼成 **`IN ()`**，MySQL 报 `1064 ... near ')'`。

    后果不是"少做了一次校验"，而是**整条命令以一大段 traceback 收场**：
    迁移早就执行完并 `commit` 了，人却以为失败了 —— 那次真跑去查库才确认没事。
    """
    assert ap.verify(碰就炸的连接(), []) == []


def test_有期望的表时_正常路径仍然真的去查了():
    """反向钉一下：别为了修上面那条，把正常路径也一起跳过了。"""
    问过的 = []

    problems = ap.verify(记事的假连接(问过的), ['product_filter_dimension'])

    assert 问过的, '_query_table_names 应当真的去查了 information_schema'
    assert problems == [], f'表与列都对得上时不该报问题，实际：{problems}'


def test_表缺失时照样报出来():
    """跳过的只有"没有期望表"这一种情况 —— 真缺表时仍必须失败。"""

    class 空结果假连接:
        def cursor(self):
            class C:
                def execute(self, sql, params):
                    pass

                def fetchall(self):
                    return []

                def __enter__(self):
                    return self

                def __exit__(self, *exc):
                    return False
            return C()

    problems = ap.verify(空结果假连接(), ['product_filter_dimension'])

    assert problems and '表缺失' in problems[0]
