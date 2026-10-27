"""写入 10 份官方云模板样例（租户 162）。

为什么直接写库：共享模板的正式流程是「会员上传私有模板 → 提交共享 → 管理员审核」，
而样例数据没有真实上传账号的登录凭据；直接写库可以同时补齐私有模板（桌面端登录后
的「我的云模板」）与共享模板（公共云模板库）。

用法：
    python scripts/seed_official_templates.py --dump-dir <目录>   # 只生成 .msdx，供解析校验
    python scripts/seed_official_templates.py                     # 校验通过后写入数据库

重复执行是幂等的：按 (租户, 会员, 名称) 复用私有模板，按 (租户, 来源模板) 复用共享模板。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime
from pathlib import Path

if __package__ in (None, ''):
    sys.path.insert(0, str(Path(__file__).resolve().parent))

TENANT_ID = 162
PUBLISHER_MEMBER_ID = 639  # 北京杏林康云（企业主账号）
SEED_MEMBER_IDS = (639, 725)  # 该企业目前的两名成员；登录任一账号都能在“我的云模板”看到样例
SEED_CREATOR = 'seed-official'
NOW = datetime.now().replace(microsecond=0)


def text(obj_id: str, x: float, y: float, w: float, h: float, value: str,
         size: float = 3.0, *, bold: bool = False, align: str = 'left',
         color: str = '#1A1B1C') -> dict:
    return {
        'id': obj_id, 'type': 'text', 'x': x, 'y': y, 'w': w, 'h': h, 'rotation': 0,
        'fontFamily': '微软雅黑', 'fontSize': size, 'bold': bold, 'align': align,
        'color': color, 'source': {'kind': 'constant', 'value': value},
    }


def barcode(obj_id: str, x: float, y: float, w: float, h: float, value: str,
            *, show_text: bool = True, symbology: str = 'code128') -> dict:
    return {
        'id': obj_id, 'type': 'barcode', 'x': x, 'y': y, 'w': w, 'h': h, 'rotation': 0,
        'symbology': symbology, 'showText': show_text,
        'source': {'kind': 'constant', 'value': value},
    }


def rect(obj_id: str, x: float, y: float, w: float, h: float,
         *, fill: str = '#FFFFFF', stroke: str = '#333333', stroke_width: float = 0.3) -> dict:
    return {
        'id': obj_id, 'type': 'rect', 'x': x, 'y': y, 'w': w, 'h': h, 'rotation': 0,
        'fill': fill, 'stroke': stroke, 'strokeWidth': stroke_width,
    }


def line(obj_id: str, x: float, y: float, w: float,
         *, stroke: str = '#999999', stroke_width: float = 0.25) -> dict:
    return {
        'id': obj_id, 'type': 'line', 'x': x, 'y': y, 'w': w, 'h': 0, 'rotation': 0,
        'stroke': stroke, 'strokeWidth': stroke_width,
    }


def doc(name: str, width_mm: float, height_mm: float, objects: list[dict]) -> dict:
    return {
        'format': 'maxlabel-msdx',
        'version': 2,
        'app': 'MaxLabel',
        'doc': {
            'version': 2,
            'name': name,
            'widthMm': width_mm,
            'heightMm': height_mm,
            'objects': objects,
        },
    }


TEMPLATES: list[dict] = [
    {
        'name': '商品价格标签',
        'category': '零售',
        'keywords': '价格,商品,条码',
        'widthMm': 60,
        'heightMm': 40,
        'envelope': doc('商品价格标签', 60, 40, [
            rect('r1', 0, 0, 60, 40),
            text('t1', 4, 3, 52, 5, 'MaxLabel 商品标签', 4, bold=True, align='center'),
            text('t2', 4, 9, 52, 5, '精选好物 · 500g', 3, align='center'),
            text('t3', 4, 15, 30, 9, '¥ 29.90', 8, bold=True, color='#CC0000'),
            barcode('b1', 4, 27, 52, 10, '6901234567892'),
        ]),
    },
    {
        'name': '零售价签',
        'category': '零售',
        'keywords': '价签,服装,吊牌价',
        'widthMm': 50,
        'heightMm': 30,
        'envelope': doc('零售价签', 50, 30, [
            rect('r1', 0, 0, 50, 30),
            text('t1', 3, 2, 44, 5, 'MAXLABEL', 4, bold=True),
            text('t2', 3, 8, 44, 4, '纯棉圆领 T 恤', 3),
            text('t3', 3, 13, 20, 4, '尺码 L', 3),
            text('t4', 3, 19, 24, 8, '¥ 99.00', 7, bold=True, color='#CC0000'),
            barcode('b1', 28, 18, 19, 9, 'ML20261027001'),
        ]),
    },
    {
        'name': '物流面单',
        'category': '物流',
        'keywords': '快递,面单,收件人',
        'widthMm': 100,
        'heightMm': 150,
        'envelope': doc('物流面单', 100, 150, [
            rect('r1', 0, 0, 100, 150),
            text('t1', 5, 4, 90, 8, 'MaxLabel 快递面单', 6, bold=True, align='center'),
            line('l1', 5, 14, 90),
            text('t2', 5, 19, 90, 5, '寄件人：张三  13800000000', 3.5),
            text('t3', 5, 26, 90, 8, '北京市朝阳区示例路 1 号', 3),
            line('l2', 5, 37, 90),
            text('t4', 5, 42, 90, 5, '收件人：李四  13900000000', 3.5),
            text('t5', 5, 49, 90, 8, '上海市浦东新区示例大道 88 号', 3),
            barcode('b1', 10, 64, 80, 25, 'SF1234567890123'),
            line('l3', 5, 94, 90),
            text('t6', 5, 99, 90, 5, '订单号：o202610270001', 3),
            text('t7', 5, 108, 90, 20, '备注：易碎品，请轻放', 3, color='#CC0000'),
        ]),
    },
    {
        'name': '食品标签',
        'category': '食品',
        'keywords': '食品,配料,生产日期',
        'widthMm': 70,
        'heightMm': 50,
        'envelope': doc('食品标签', 70, 50, [
            rect('r1', 0, 0, 70, 50),
            text('t1', 4, 3, 62, 6, '手工曲奇饼干', 5, bold=True, align='center'),
            text('t2', 4, 11, 62, 10, '配料：小麦粉、黄油、白砂糖、鸡蛋', 3),
            text('t3', 4, 23, 30, 5, '净含量：200g', 3),
            text('t4', 4, 30, 40, 5, '生产日期：2026-10-27', 3),
            barcode('b1', 4, 38, 62, 9, '6921234567890'),
        ]),
    },
    {
        'name': '药品标签',
        'category': '医药',
        'keywords': '药品,规格,用法用量',
        'widthMm': 80,
        'heightMm': 50,
        'envelope': doc('药品标签', 80, 50, [
            rect('r1', 0, 0, 80, 50),
            text('t1', 4, 3, 72, 6, '阿莫西林胶囊', 5, bold=True),
            text('t2', 4, 11, 72, 5, '规格：0.25g × 24 粒', 3),
            text('t3', 4, 18, 72, 10, '用法用量：口服，一次 2 粒，一日 3 次', 3),
            text('t4', 4, 30, 72, 5, '请遵医嘱 · 孕妇禁用', 3, color='#CC0000'),
            barcode('b1', 4, 38, 72, 9, '6909876543210'),
        ]),
    },
    {
        'name': '服装吊牌',
        'category': '服装',
        'keywords': '吊牌,尺码,品牌',
        'widthMm': 40,
        'heightMm': 80,
        'envelope': doc('服装吊牌', 40, 80, [
            rect('r1', 0, 0, 40, 80),
            text('t1', 3, 3, 34, 7, 'MAXLABEL', 5, bold=True, align='center'),
            text('t2', 3, 12, 34, 4, 'URBAN COLLECTION', 2.5, align='center'),
            line('l1', 6, 19, 28),
            text('t3', 3, 25, 34, 10, 'L', 10, bold=True, align='center'),
            text('t4', 3, 39, 34, 8, '¥ 299.00', 6, bold=True, align='center', color='#CC0000'),
            barcode('b1', 5, 51, 30, 14, 'ML20261027L'),
            text('t5', 3, 69, 34, 5, '100% 棉', 3, align='center'),
        ]),
    },
    {
        'name': '固定资产标签',
        'category': '企业',
        'keywords': '固定资产,资产编号,部门',
        'widthMm': 60,
        'heightMm': 30,
        'envelope': doc('固定资产标签', 60, 30, [
            rect('r1', 0, 0, 60, 30),
            text('t1', 3, 2, 54, 5, '北京杏林康云 · 固定资产', 3, bold=True),
            text('t2', 3, 8, 54, 6, '资产编号：ZC-2026-0001', 3.5),
            text('t3', 3, 15, 40, 4, '使用部门：信息中心', 3),
            barcode('b1', 3, 21, 54, 7, 'ZC-2026-0001'),
        ]),
    },
    {
        'name': '珠宝首饰标签',
        'category': '珠宝',
        'keywords': '珠宝,价格,首饰',
        'widthMm': 40,
        'heightMm': 20,
        'envelope': doc('珠宝首饰标签', 40, 20, [
            rect('r1', 0, 0, 40, 20),
            text('t1', 2, 1.5, 36, 4, '杏林珠宝', 3, bold=True, align='center'),
            text('t2', 2, 6, 36, 4, '足金 999 项链', 2.5, align='center'),
            text('t3', 2, 10.5, 36, 5, '¥ 1,880.00', 4, bold=True, align='center', color='#CC0000'),
            text('t4', 2, 16, 36, 3.5, 'NO. A20261027', 2.2, align='center'),
        ]),
    },
    {
        'name': '仓库库位标签',
        'category': '仓储',
        'keywords': '库位,仓库,条码',
        'widthMm': 100,
        'heightMm': 60,
        'envelope': doc('仓库库位标签', 100, 60, [
            rect('r1', 0, 0, 100, 60),
            text('t1', 5, 4, 90, 8, '库位：A-01-02-03', 7, bold=True, align='center'),
            line('l1', 5, 14, 90),
            text('t2', 5, 19, 40, 5, '区域：A 区', 4),
            text('t3', 5, 26, 40, 5, '类型：常温', 4),
            barcode('b1', 5, 36, 90, 18, 'A-01-02-03'),
        ]),
    },
    {
        'name': '快递小面单',
        'category': '物流',
        'keywords': '快递,面单,小面单',
        'widthMm': 76,
        'heightMm': 130,
        'envelope': doc('快递小面单', 76, 130, [
            rect('r1', 0, 0, 76, 130),
            text('t1', 4, 3, 68, 6, 'MaxLabel 小面单', 5, bold=True, align='center'),
            line('l1', 4, 11, 68),
            text('t2', 4, 15, 68, 5, '收：王五  13700000000', 3.2),
            text('t3', 4, 22, 68, 12, '广东省深圳市南山区示例路 66 号', 3),
            barcode('b1', 6, 38, 64, 20, 'YT9876543210987'),
            line('l2', 4, 62, 68),
            text('t4', 4, 67, 68, 5, '订单：YT20261027001', 3),
            text('t5', 4, 77, 68, 20, '温馨提示：请当面验货', 3),
            text('t6', 4, 114, 68, 5, '打印日期：2026-10-27', 2.5),
        ]),
    },
]


def serialized() -> list[dict]:
    result = []
    for item in TEMPLATES:
        content = json.dumps(item['envelope'], ensure_ascii=False, indent=2)
        result.append({
            **{key: item[key] for key in ('name', 'category', 'keywords', 'widthMm', 'heightMm')},
            'content': content,
            'sizeBytes': len(content.encode('utf-8')),
            'contentHash': hashlib.sha256(content.encode('utf-8')).hexdigest(),
        })
    return result


def dump(directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for item in serialized():
        path = directory / f"{item['name']}.msdx"
        path.write_text(item['content'], encoding='utf-8', newline='\n')
        print(f'wrote {path} ({item["sizeBytes"]} bytes)')


def seed() -> None:
    import pymysql
    from _dbtunnel import DBTunnel, _env

    env = _env()
    templates = serialized()
    with DBTunnel() as (host, port):
        conn = pymysql.connect(host=host, port=port, user='root',
                               password=env['MYSQL_DB_SERVER_PASSWRD'],
                               database='ruoyi-vue-pro', charset='utf8mb4')
        try:
            created_private = 0
            by_member_name: dict[tuple[int, str], int] = {}
            with conn.cursor() as cur:
                for member_id in SEED_MEMBER_IDS:
                    for item in templates:
                        cur.execute(
                            'SELECT id FROM maxlabel_label_template '
                            'WHERE tenant_id=%s AND member_id=%s AND name=%s AND deleted=0 LIMIT 1',
                            (TENANT_ID, member_id, item['name']))
                        row = cur.fetchone()
                        if row:
                            template_id = int(row[0])
                            cur.execute(
                                'UPDATE maxlabel_label_template SET content=%s, size_bytes=%s, content_hash=%s, '
                                'width_mm=%s, height_mm=%s, category=%s, keywords=%s, updater=%s '
                                'WHERE id=%s',
                                (item['content'], item['sizeBytes'], item['contentHash'],
                                 item['widthMm'], item['heightMm'], item['category'],
                                 item['keywords'], SEED_CREATOR, template_id))
                        else:
                            cur.execute(
                                'INSERT INTO maxlabel_label_template '
                                '(member_id, name, content, size_bytes, content_hash, width_mm, height_mm, '
                                ' category, keywords, creator, updater, tenant_id) '
                                'VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)',
                                (member_id, item['name'], item['content'], item['sizeBytes'],
                                 item['contentHash'], item['widthMm'], item['heightMm'],
                                 item['category'], item['keywords'], SEED_CREATOR, SEED_CREATOR,
                                 TENANT_ID))
                            template_id = int(cur.lastrowid)
                            created_private += 1
                        by_member_name[(member_id, item['name'])] = template_id

                created_shared = 0
                for item in templates:
                    source_id = by_member_name[(PUBLISHER_MEMBER_ID, item['name'])]
                    cur.execute(
                        'SELECT id FROM maxlabel_shared_template '
                        'WHERE tenant_id=%s AND source_template_id=%s AND deleted=0 LIMIT 1',
                        (TENANT_ID, source_id))
                    row = cur.fetchone()
                    if row:
                        cur.execute(
                            'UPDATE maxlabel_shared_template SET name=%s, content=%s, size_bytes=%s, '
                            'width_mm=%s, height_mm=%s, category=%s, keywords=%s, '
                            'audit_status=1, reject_reason=NULL, audit_user_id=1, audit_time=%s, '
                            'published_time=%s, updater=%s WHERE id=%s',
                            (item['name'], item['content'], item['sizeBytes'], item['widthMm'],
                             item['heightMm'], item['category'], item['keywords'], NOW, NOW,
                             SEED_CREATOR, int(row[0])))
                    else:
                        cur.execute(
                            'INSERT INTO maxlabel_shared_template '
                            '(source_template_id, publisher_member_id, name, content, size_bytes, '
                            ' width_mm, height_mm, category, keywords, audit_status, audit_user_id, '
                            ' audit_time, published_time, creator, updater, tenant_id) '
                            'VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,1,1,%s,%s,%s,%s,%s)',
                            (source_id, PUBLISHER_MEMBER_ID, item['name'], item['content'],
                             item['sizeBytes'], item['widthMm'], item['heightMm'], item['category'],
                             item['keywords'], NOW, NOW, SEED_CREATOR, SEED_CREATOR, TENANT_ID))
                        created_shared += 1

            conn.commit()
            print(f'private templates: +{created_private}, shared templates: +{created_shared}')
            print(f'seed members: {SEED_MEMBER_IDS}; shared publisher: {PUBLISHER_MEMBER_ID}')
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description='Seed 10 official MaxLabel cloud templates for tenant 162.')
    parser.add_argument('--dump-dir', type=Path, default=None,
                        help='Only write .msdx files to this directory; do not touch the database.')
    args = parser.parse_args()
    if args.dump_dir:
        dump(args.dump_dir)
        return
    seed()


if __name__ == '__main__':
    main()
