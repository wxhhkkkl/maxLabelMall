"""deploy 纯函数层的单测。

只测「该传哪些文件」这类判断。连服务器、跑 systemctl、重启后等待那些步骤
**不在这里假装覆盖** —— 它们只能靠一次真实的 dry-run + 发版后人工确认。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import deploy as dp  # noqa: E402


def remote_of(sizes):
    """伪造「远程已有文件及其大小」，查不到的返回 None（等于远程没这个文件）。"""
    return lambda rel: sizes.get(rel)


# ── index.html：绝不能按大小跳过 ────────────────────────────────────────────────

def test_index_html_大小完全相同也必须重传():
    """⚠️ 这是 2026-10-10 真实踩过的坑。

    新旧两版 `index.html` **恰好都是 575 字节**（只差入口 hash 那几个字符，
    长度一样）。当时部署脚本用了「同名同大小就跳过」的优化，于是它被静默跳过 ——
    表现是**资产全换成新的了、首页还是旧的**，而且脚本一路打印"成功"。
    """
    files = [('index.html', 575)]
    send, skip = dp.plan_uploads(files, remote_of({'index.html': 575}))
    assert send == ['index.html']
    assert skip == []


def test_index_html_大小不同当然要传():
    files = [('index.html', 600)]
    send, skip = dp.plan_uploads(files, remote_of({'index.html': 575}))
    assert send == ['index.html']
    assert skip == []


def test_index_html_远程没有要传():
    send, _ = dp.plan_uploads([('index.html', 575)], remote_of({}))
    assert send == ['index.html']


# ── 哈希命名的资产：同名即同内容，可跳过 ────────────────────────────────────────

def test_哈希资产同名同大小可以跳过():
    """文件名带内容哈希，同名必然是同一份内容 —— 跳过是安全的、也是省时间的关键。"""
    files = [('assets/index-AAA.js', 100), ('assets/index-BBB.js', 200)]
    send, skip = dp.plan_uploads(
        files, remote_of({'assets/index-AAA.js': 100, 'assets/index-BBB.js': 200})
    )
    assert send == []
    assert skip == ['assets/index-AAA.js', 'assets/index-BBB.js']


def test_哈希资产大小不同要传():
    """理论上不该发生（同名同内容），但真碰上就得传 —— 宁可多传一个也别留旧内容。"""
    send, _ = dp.plan_uploads([('assets/index-AAA.js', 101)], remote_of({'assets/index-AAA.js': 100}))
    assert send == ['assets/index-AAA.js']


def test_哈希资产远程没有要传():
    send, _ = dp.plan_uploads([('assets/index-NEW.js', 10)], remote_of({}))
    assert send == ['assets/index-NEW.js']


# ── 混合与目录层级 ─────────────────────────────────────────────────────────────

def test_只增不删_远程多出来的文件不参与判断():
    """`assets/` 里的旧哈希文件是孤儿，但**必须留着** —— 还开着旧 index.html 的会话
    拉分片时全靠它们。所以差异计算只看本地文件，远程多的那些一概不碰。
    """
    files = [('index.html', 575), ('assets/index-NEW.js', 10)]
    send, skip = dp.plan_uploads(files, remote_of({'index.html': 575, 'assets/index-OLD.js': 99}))
    assert send == ['index.html', 'assets/index-NEW.js']
    assert skip == []


def test_子目录里的图片也按同样规则走():
    files = [('assets/solutions/a.webp', 1000), ('assets/home/b.webp', 2000)]
    send, skip = dp.plan_uploads(
        files, remote_of({'assets/solutions/a.webp': 1000, 'assets/home/b.webp': 999})
    )
    assert send == ['assets/home/b.webp']
    assert skip == ['assets/solutions/a.webp']


# ── jar 里的「IDE 编坏的 class」门禁（2026-10-11 生产事故后加的）──────────────────
#
# 事故：本机开着 VSCode 的 Java 语言服务器时，它按自己的 classpath 把工作区编进
# `target/classes`，其中有带编译错误的 class（ECJ 会照样产出，只是运行到就抛
# `Unresolved compilation problems`）。它写在 11:42:10，jar 打好在 11:42:12 —— 被卷了进去。
# 表现是 BUILD SUCCESS、启动也正常，但一调 `ProductSpuController.getSpuDetail` 就 500。

def _make_fat_jar(tmp_path, *, polluted=False):
    """造一个最小的 Spring Boot 胖 jar：BOOT-INF/classes + 一个嵌套的 yudao-*.jar"""
    import zipfile

    jar = tmp_path / 'yudao-server.jar'
    ok_class = b'\xca\xfe\xba\xbe' + b'normal bytecode'
    bad_class = b'\xca\xfe\xba\xbe' + dp.ECJ_MARKER + b'...'

    nested = tmp_path / 'yudao-module-product-server.jar'
    with zipfile.ZipFile(nested, 'w') as nz:
        nz.writestr('cn/iocoder/yudao/module/product/convert/spu/ProductSpuConvertImpl.class',
                    bad_class if polluted else ok_class)
        nz.writestr('cn/iocoder/yudao/module/product/service/spu/ProductSpuServiceImpl.class', ok_class)
    # 第三方依赖不进扫描范围 —— 它不可能被 IDE 编译
    other = tmp_path / 'guava.jar'
    with zipfile.ZipFile(other, 'w') as oz:
        oz.writestr('com/google/Foo.class', bad_class)

    with zipfile.ZipFile(jar, 'w') as z:
        z.writestr('BOOT-INF/classes/application.yaml', b'key: value')
        z.writestr('BOOT-INF/classes/cn/iocoder/yudao/Application.class', ok_class)
        z.writestr('BOOT-INF/lib/yudao-module-product-server.jar', nested.read_bytes())
        z.writestr('BOOT-INF/lib/guava.jar', other.read_bytes())
    return jar


def test_scan_jar_for_ecj_detects_polluted_class(tmp_path):
    hits = dp.scan_jar_for_ecj(_make_fat_jar(tmp_path, polluted=True))
    assert len(hits) == 1
    assert 'ProductSpuConvertImpl.class' in hits[0]
    assert hits[0].startswith('BOOT-INF/lib/yudao-module-product-server.jar!')


def test_scan_jar_for_ecj_clean_jar(tmp_path):
    assert dp.scan_jar_for_ecj(_make_fat_jar(tmp_path, polluted=False)) == []


def test_scan_jar_for_ecj_ignores_third_party_libs(tmp_path):
    """第三方 jar 里就算有同样字样的类也不该报 —— 它不可能被 IDE 编译，
    而扫全部依赖会让这个检查慢到没人愿意跑。"""
    import zipfile

    jar = tmp_path / 'yudao-server.jar'
    other = tmp_path / 'guava.jar'
    with zipfile.ZipFile(other, 'w') as oz:
        oz.writestr('com/google/Foo.class', dp.ECJ_MARKER)
    with zipfile.ZipFile(jar, 'w') as z:
        z.writestr('BOOT-INF/lib/guava.jar', other.read_bytes())

    assert dp.scan_jar_for_ecj(jar) == []
