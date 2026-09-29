#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""旧商城（u247521.yz168.cc）历史数据 → 当前系统（yudao，租户 162 御旺宸发）。

用法：
    python scripts/import_legacy_mall.py parse   <xlsx>          # 只解析，打印统计
    python scripts/import_legacy_mall.py dry-run <xlsx>          # 打印将写入的每条记录（不连库）
    python scripts/import_legacy_mall.py backup                  # 备份受影响的表 → .sql
    python scripts/import_legacy_mall.py apply   <xlsx> --yes    # 真正写入
    python scripts/import_legacy_mall.py verify                  # 读回核验
    python scripts/import_legacy_mall.py rollback --yes          # 按 manifest 回滚

── 为什么是「admin-api + 直连 SQL」两条路 ────────────────────────────────────
  · 分类与商品走 **admin-api**：SPU 的价格/库存是 SKU 的聚合（ProductSpuServiceImpl
    .initSpuFromSkus），分类必须挂在二级、品牌必须存在，这些规则由服务端裁决。
    用 SQL 手写等于把服务端业务规则抄一遍 —— 宪法原则 III 禁止。
    （仓库里已有的 storefront/scripts/seed-demo-tenant.py 是这条路的先例。）
  · 会员与订单走 **直连 SQL**：yudao 的 /admin-api/member/user 没有 create、
    /admin-api/trade/order 也没有 create（已逐文件核实），只能直写库。

── 几个数字为什么这么算 ──────────────────────────────────────────────────────
  · 金额一律 Decimal 换算：6.2 元 × 100 用浮点得 619.9999999999999。
  · 简介与详情一律 [[...]] 占位：旧系统没导出这两项，编造即等于在正式站点发布
    虚假商品信息（宪法 FR-054）。
  · 订单金额取旧系统原值，**不用现行商品价重算**：`赋签 MaxLabel 软件授权费`
    一类商品改过价，只有 85.6% 的订单能用现行价对上，14% 对不上。
"""

import argparse
import csv
import datetime
import decimal
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parent.parent
XLSX = str(ROOT / 'data-collection(1).xlsx')
SCRIPTS = ROOT / 'scripts'
MANIFEST = SCRIPTS / 'import-legacy-mall-manifest.json'
REPORT = SCRIPTS / 'import-legacy-mall-report.md'
BACKUP_DIR = SCRIPTS / '_backup'
YAML = ROOT / 'yudao-cloud/yudao-server/src/main/resources/application-my.yaml'

# ── 目标系统的固定约定 ────────────────────────────────────────────────────────
TENANT_ID = 162                 # 业务租户「御旺宸发」
SOURCE_URL = 'u247521.yz168.cc'
PIC_URL = '/assets/logo.png'    # 全站唯一图片资产；现有 14 个分类用的也是它
BRAND_NAME = '御旺宸发'          # 租户 162 已有品牌（id=4），复用不新建
ADMIN_BASE = 'http://localhost:48080/admin-api'
ADMIN_USER = 'admin'
ADMIN_PWD = 'admin123'          # 与仓库既有 seed-demo-tenant.py 一致

MIGRATION_TAG = '迁移自旧商城 %s' % SOURCE_URL
PENDING = '[[%s，原系统未导出，待补充]]'

# 旧系统的「无限库存」哨兵：2147417040 = int 上限 - 15。仅 DZ001「不干胶定制」一条。
STOCK_SENTINEL = 2147417040
STOCK_SENTINEL_REPLACEMENT = 999999

# 旧系统订单状态码（项目所有者 2026-09-29 提供）→ (yudao status, yudao refund_status)
#   yudao: 0 待付款 / 10 待发货 / 20 已发货 / 30 已完成 / 40 已取消
#   refund_status: 0 未退款 / 10 部分退款 / 20 全部退款
STATUS_MAP = {
    '2': (30, 0),    # 交易成功            → 已完成
    '3': (20, 0),    # 等待收货            → 已发货
    '4': (40, 0),    # 交易关闭            → 已取消
    '6': (40, 20),   # 退款完成            → 已取消 + 全部退款
    '8': (20, 0),    # 货到付款，已发货     → 已发货
}

# 4 个商品引用了「分类表里没有的路径」。前 3 个其实是叶子节点，导出时漏写了父级名；
# 第 4 个是真·未分类，按商品名（「高等铜版纸」）人工归入 —— 这是本次唯一一处人工判定。
ORPHAN_CATEGORY_MAP = {
    '经济热敏': ('卷筒标签', '经济热敏'),
    '蜡基': ('碳带', '蜡基'),
    '宸发服务': ('软件服务', '宸发服务'),
    '未分类': ('卷筒标签', '高等铜版'),   # ← 人工判定，见报告
}


# ══════════════════════════════════════════════════════════════════════════════
# 纯函数层：解析与转换。与外部世界无关，由 test_import_legacy_mall.py 覆盖。
# ══════════════════════════════════════════════════════════════════════════════

def load_sheets(path):
    """读 xlsx → {sheet 名: [ {列名: 字符串} ]}，跳过全空行。"""
    wb = openpyxl.load_workbook(path, data_only=True)
    out = {}
    for ws in wb.worksheets:
        rows = list(ws.iter_rows(values_only=True))
        if not rows:
            continue
        header = [str(h) for h in rows[0]]
        data = []
        for r in rows[1:]:
            if all(c is None or str(c).strip() == '' for c in r):
                continue
            data.append({h: (None if v is None else str(v)) for h, v in zip(header, r)})
        out[ws.title] = data
    return out


def s(v):
    return (v or '').strip()


def yuan_to_fen(v):
    """元 → 分。整数返回，绝不经过 float。

    允许负号：流水表用金额正负表示方向（「支出」行是 -190）。
    ⚠️ 本数据集里 282 个商品价中有 16 个用 float 换算会错 1 分，例如
       37.66 * 100 == 3765.9999999999995 → int() 得 3765，少 1 分。
    """
    t = s(v)
    if not re.fullmatch(r'-?\d+(\.\d{1,2})?', t):
        raise ValueError('不是合法的元金额: %r' % v)
    return int((decimal.Decimal(t) * 100).to_integral_value())


def norm_name(v):
    """商品名归一化，用于把「订单里的商品名」对上「商品表里的商品名」。

    订单明细里的名字与商品表**不是逐字一致**：空格、全半角括号、以及 `*` / `×` / `x`
    三种尺寸分隔符混用（`50*20` / `50×20` / `50x20` 是同一个规格）。三种分隔符统一成
    `x` 之后，486 行明细的匹配率从 72.8% 提到 75.5%（匹配不上 119 行、63 个名称）。
    """
    t = s(v).lower().replace('×', 'x').replace('*', 'x')
    for ch in ' \t【】[]（）()、,，':
        t = t.replace(ch, '')
    return t


def parse_multiline(v):
    return [x.strip() for x in s(v).split('\n') if x.strip()]


def map_order_status(code):
    """旧状态码 → (yudao status, yudao refund_status)。表里没有的一律报错，不猜。"""
    t = s(code)
    if t not in STATUS_MAP:
        raise ValueError('未知的旧订单状态码: %r（请补充 STATUS_MAP）' % code)
    return STATUS_MAP[t]


def build_categories(sheet):
    """42 行 → 7 个一级 + 35 个二级。sort 用旧系统的页面行序号。"""
    rows = []
    for r in sheet:
        parent = s(r['上级分类(ParentName)'])
        rows.append({
            'name': s(r['分类名称(Name)']),
            'parent_key': parent or None,
            'sort': int(s(r['页面行序号(VisibleOrder)']) or 0),
        })
    rows.sort(key=lambda r: r['sort'])
    return rows


def _resolve_category(pname):
    """'卷筒标签,经济热敏' → (父, 叶)；4 个孤儿路径走显式映射表。"""
    t = s(pname)
    if ',' in t:
        parent, leaf = t.split(',', 1)
        return s(parent), s(leaf)
    if t in ORPHAN_CATEGORY_MAP:
        return ORPHAN_CATEGORY_MAP[t]
    raise ValueError('分类路径既不是「父,子」也不在孤儿映射表里: %r' % pname)


def build_products(sheet, cats):
    """282 行 → 282 个商品（编号重复、跨分类，已确认不合并为多规格）。"""
    leaves = {(c['parent_key'], c['name']) for c in cats if c['parent_key']}
    out = []
    for r in sheet:
        parent, leaf = _resolve_category(r['分类(pName)'])
        if (parent, leaf) not in leaves:
            raise ValueError('解析出的分类不在分类表里: %s/%s' % (parent, leaf))
        stock = int(s(r['库存(TotalQuantity)']) or 0)
        if stock == STOCK_SENTINEL:
            stock = STOCK_SENTINEL_REPLACEMENT
        name = s(r['产品名称(Name)'])
        out.append({
            'serial': s(r['商品编号(SerialNum)']),
            'name': name,
            'price': yuan_to_fen(r['价格(Price)']),
            'stock': stock,
            'sales_count': int(s(r['销量(SalesCount)']) or 0),
            'browse_count': int(s(r['点击数(Hits)']) or 0),
            'category_parent': parent,
            'category_leaf': leaf,
            'keyword': s(r['商品编号(SerialNum)']),   # 保留旧编号，便于按编号检索/对账
            'pic_url': PIC_URL,
            'introduction': PENDING % '商品简介',
            'description': '<p>%s</p>' % (PENDING % '商品详情'),
        })
    return out


def build_members(sheet):
    """91 行 → 86 个可导入会员 + 5 个无手机号被跳过的（列清单，不造占位号）。"""
    importable, skipped = [], []
    for r in sheet:
        legacy_id = s(r['会员编号(UserID)'])
        mobile = s(r['电话(Mobile)'])
        if not re.fullmatch(r'1\d{10}', mobile):
            skipped.append({'legacy_id': legacy_id, 'nickname': s(r['会员名(sNickName)']),
                            'reason': '无手机号' if not mobile else '手机号非法: %s' % mobile})
            continue
        nickname = s(r['会员名(sNickName)']) or legacy_id
        importable.append({
            'legacy_id': legacy_id,
            'mobile': mobile,
            'nickname': nickname[:30],          # member_user.nickname 是 varchar(30)
            'create_time': s(r['注册时间(CrTime)']) or None,
            'point': int(float(s(r['积分(Point)']) or 0)),
            'mark': '%s，原会员编号 %s' % (MIGRATION_TAG, legacy_id),
        })
    return importable, skipped


def build_finance_index(finance_sheet):
    """670 条流水 → {订单号: {'pay_time', 'amount_fen'}}（只取「支出」那 335 条）。

    流水不入库（余额全 0、无资产可迁），但它的两个字段有用：
      · 支付时间 —— 比拿「下单时间」当支付时间更准；
      · 扣款额   —— 与订单的「实付」逐笔吻合，是退款金额的可靠来源。
    """
    idx = {}
    for r in finance_sheet:
        order_no = s(r['关联订单号(OrderID)'])
        if not order_no or s(r['方向(Money正负)']) != '支出':
            continue
        idx[order_no] = {'pay_time': s(r['支付时间(PayTime)']) or None,
                         'amount_fen': abs(yuan_to_fen(r['发生金额(Money)']))}
    return idx


_SPEC_SUFFIX = re.compile(r'[【\[（(][^】\]）)]*[】\]）)]\s*$')


def classify_unmatched(unmatched, products):
    """把「匹配不上的名称」分成两类，**只用于报告，不参与写入**：

      · 可恢复 —— 去掉尾部【规格】后能与商品表对上。旧系统的「多规格」商品在订单里
        带规格后缀（`多规格哑银不干胶标签【100*70*500单排 】`），而商品表里只导出
        了父商品名（`多规格哑银不干胶标签`）。**这类不做自动关联**：父商品的价
        不等于被下单那个规格的价，硬联过去等于给历史订单编一个单价。
      · 不可恢复 —— 旧系统里确实已无对应商品（改名/下架）。
    """
    index = {norm_name(p['name']) for p in products}
    recoverable, gone = [], []
    for n in unmatched:
        base = _SPEC_SUFFIX.sub('', n).strip()
        target = recoverable if (base != n and norm_name(base) in index) else gone
        target.append(n)
    return recoverable, gone


def match_product(name, product_index):
    """订单里的商品名 → (商品在商品表里的下标, 商品)。归一化后精确匹配，不做模糊猜测。

    返回**下标**而不是商品编号：旧系统的商品编号有 32 个重复（72 行），
    跨材质复用（如 LGTD100300 同时是全树脂与混合基），拿编号当主键会串货。
    """
    return product_index.get(norm_name(name))


def build_orders(order_sheet, finance_sheet, members, products):
    """393 张订单 + 486 行明细。

    返回 (orders, items, unmatched_names, unknown_members)：
      · unmatched_names —— 明细里在商品表找不到的**去重**名称（spu_id 记 0，列清单）；
      · unknown_members —— 订单引用了但会员表里没有的会员编号（应当为空）。
    """
    by_legacy_id = {m['legacy_id']: m for m in members}
    product_index = {}
    for idx, p in enumerate(products):
        product_index.setdefault(norm_name(p['name']), (idx, p))
    finance = build_finance_index(finance_sheet)

    orders, items, unmatched, unknown = [], [], set(), []
    for r in order_sheet:
        legacy_no = s(r['订单编号(OrderID)'])
        legacy_status = s(r['状态(Status)'])
        status, refund_status = map_order_status(legacy_status)

        total_fen = yuan_to_fen(r['订单总额(Money)'])
        paid_raw = s(r['金额(ModifiedMoney)'])
        pay_fen = yuan_to_fen(paid_raw) if paid_raw else total_fen
        # yudao 的算法：应付 = 原价 − 优惠 + 运费 + 调价（运费无数据，记 0）
        discount_fen = max(0, total_fen - pay_fen)
        adjust_fen = max(0, pay_fen - total_fen)

        legacy_uid = s(r['会员编号(UserID)'])
        member = by_legacy_id.get(legacy_uid)
        if member is None and legacy_uid not in [u['legacy_id'] for u in unknown]:
            unknown.append({'order_no': legacy_no, 'member_legacy_id': legacy_uid})

        names = parse_multiline(r['产品(Goods[].ProductName;Goods[].ProNorms)'])
        amounts = [int(x) for x in parse_multiline(r['数量(Goods[].Amount)'])]
        create_time = s(r['下单时间(CrTime)'])
        fin = finance.get(legacy_no) or {}
        # 支付时间优先取流水；流水没有的（线下付款）回落到下单时间 —— 见报告「推导值」
        paid = status in (20, 30) or refund_status == 20
        pay_time = (fin.get('pay_time') or create_time) if paid else None

        orders.append({
            'no': legacy_no,
            'legacy_status': legacy_status,
            'user_legacy_id': legacy_uid,
            'status': status,
            'refund_status': refund_status,
            'refund_price': fin['amount_fen'] if (refund_status == 20 and fin) else 0,
            'pay_status': 1 if paid else 0,
            'pay_time': pay_time,
            'create_time': create_time,
            'finish_time': s(r['完成时间(ConfirmTime)']) or None,
            'total_price': total_fen,
            'discount_price': discount_fen,
            'adjust_price': adjust_fen,
            'pay_price': pay_fen,
            'product_count': sum(amounts),
            'delivery_type': 1,
            'terminal': 0,               # 旧系统未导出终端来源，记「未知」而非猜
            'brokerage_user_id': None,   # 推荐人字段只有 0 / -1，无真实推荐关系
            'logistics_no': s(r['快递单号(InvoiceNo)']) or None,
            'receiver_name': s(r['收件人(Contact)']),
            'receiver_mobile': s(r['联系电话(Mobile)']),
            'remark': MIGRATION_TAG,
            'legacy_paid': paid_raw,
            'legacy_total': s(r['订单总额(Money)']),
            '_names': names,
            '_amounts': amounts,
        })

        for name, amount in zip(names, amounts):
            hit = match_product(name, product_index)
            if hit is None:
                unmatched.add(name)
            prod_idx, prod = hit if hit else (None, None)
            unit = prod['price'] if prod else 0
            items.append({
                'order_no': legacy_no,
                'user_legacy_id': legacy_uid,
                'spu_name': name,                      # 保留订单里的历史名称（即使现已改名）
                'product_idx': prod_idx,               # 写入时的权威键（编号会重复）
                'product_serial': prod['serial'] if prod else None,
                'price': unit,
                'count': amount,
                'pay_price': unit * amount,
                'pic_url': prod['pic_url'] if prod else None,
            })

    return orders, items, sorted(unmatched), unknown


# ══════════════════════════════════════════════════════════════════════════════
# 外部世界：admin-api 客户端（分类/商品）
# ══════════════════════════════════════════════════════════════════════════════

class AdminApi:
    """照 storefront/scripts/seed-demo-tenant.py 的机制：
    `tenant-id: 1` 鉴权（超管属于租户 1）+ `visit-tenant-id: 162` 切数据上下文。"""

    def __init__(self, base=ADMIN_BASE):
        self.base = base
        self.token = None

    def call(self, method, path, body=None):
        req = urllib.request.Request(self.base + path, method=method)
        req.add_header('tenant-id', '1')
        if self.token:
            req.add_header('Authorization', 'Bearer ' + self.token)
            req.add_header('visit-tenant-id', str(TENANT_ID))
        data = None
        if body is not None:
            req.add_header('Content-Type', 'application/json; charset=utf-8')
            data = json.dumps(body, ensure_ascii=False).encode('utf-8')
        try:
            with urllib.request.urlopen(req, data, timeout=60) as r:
                res = json.loads(r.read().decode('utf-8'))
        except urllib.error.URLError as e:
            raise SystemExit('✗ 无法访问 %s（后端没起？）: %s' % (self.base, e))
        if res.get('code') != 0:
            raise SystemExit('✗ %s %s 失败: %s' % (method, path, res.get('msg')))
        return res.get('data')

    def login(self):
        self.token = self.call('POST', '/system/auth/login',
                               {'username': ADMIN_USER, 'password': ADMIN_PWD})['accessToken']

    def brands(self):
        return self.call('GET', '/product/brand/list') or []

    def categories(self):
        return self.call('GET', '/product/category/list') or []

    def spu_page(self, page_size=100):
        return self.call('GET', '/product/spu/page?pageNo=1&pageSize=%d' % page_size)

    def create_category(self, parent_id, name, sort):
        return self.call('POST', '/product/category/create', {
            'parentId': parent_id, 'name': name, 'picUrl': PIC_URL,
            'sort': sort, 'status': 0,
        })

    def delete_category(self, cid):
        return self.call('DELETE', '/product/category/delete?id=%d' % cid)

    def create_spu(self, p, category_id, brand_id):
        return self.call('POST', '/product/spu/create', {
            'name': p['name'],
            'keyword': p['keyword'],
            'introduction': p['introduction'],
            'description': p['description'],
            'categoryId': category_id,
            'brandId': brand_id,
            'picUrl': p['pic_url'],
            'sliderPicUrls': [p['pic_url']],
            'sort': 0,
            'specType': False,          # 单规格：前台不渲染规格表
            'deliveryTypes': [1],       # 1 = 快递
            'giveIntegral': 0,
            'subCommissionType': False,
            'skus': [{
                'name': p['name'], 'price': p['price'], 'marketPrice': 0,
                'picUrl': p['pic_url'], 'stock': p['stock'], 'weight': 0,
            }],
        })

    def delete_spu(self, spu_id):
        """yudao 删商品是**两步**：必须先置 -1（回收站），否则报「不处于回收站状态」
        （ProductSpuServiceImpl.deleteSpu:168）。"""
        self.call('PUT', '/product/spu/update-status', {'id': spu_id, 'status': -1})
        return self.call('DELETE', '/product/spu/delete?id=%d' % spu_id)


# ══════════════════════════════════════════════════════════════════════════════
# 外部世界：数据库客户端（会员/订单）
# ══════════════════════════════════════════════════════════════════════════════

def db_config():
    """从后端自己的 application-my.yaml 读 datasource.master —— 不新增任何凭据。

    ⚠️ 必须按 YAML 结构定位：该文件里**第一个** password 是 Redis 的。
    """
    import yaml
    cfg = yaml.safe_load(open(YAML, encoding='utf-8'))
    ds = cfg['spring']['datasource']['dynamic']['datasource']['master']
    m = re.search(r'jdbc:mysql://([^:/]+):(\d+)/([^?]+)', ds['url'])
    return dict(host=m.group(1), port=int(m.group(2)), user=ds['username'],
                password=ds['password'], database=m.group(3), charset='utf8mb4')


def connect():
    import pymysql
    return pymysql.connect(**db_config())


AFFECTED_TABLES = ['product_category', 'product_spu', 'product_sku',
                   'member_user', 'trade_order', 'trade_order_item']


def dump_tables(conn):
    """把受影响的表在租户 162 下的数据导出成可回灌的 .sql（纯文本，方便肉眼核对）。

    返回 (路径, 快照)。**快照这份内存副本是必须的**：manifest 里「被删掉的示例数据」
    必须来自这里，不能来自删除后再查接口 —— 首次跑失败时 5 个示例商品已被逻辑删除，
    接口查不到了，manifest 若那时才去采集就会漏记，回滚就恢复不出来。
    """
    BACKUP_DIR.mkdir(exist_ok=True)
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    path = BACKUP_DIR / ('tenant162-before-%s.sql' % stamp)
    snapshot = {}
    with open(path, 'w', encoding='utf-8') as f:
        f.write('-- 导入前备份：租户 %d 的受影响表\n-- 生成时间 %s\n\n' % (TENANT_ID, stamp))
        with conn.cursor() as cur:
            for t in AFFECTED_TABLES:
                cur.execute('SELECT * FROM %s WHERE tenant_id=%%s' % t, (TENANT_ID,))
                cols = [d[0] for d in cur.description]
                rows = [dict(zip(cols, r)) for r in cur.fetchall()]
                snapshot[t] = rows
                f.write('-- %s: %d 行\n' % (t, len(rows)))
                for row in rows:
                    vals = []
                    for c in cols:
                        v = row[c]
                        if v is None:
                            vals.append('NULL')
                        elif isinstance(v, (int, float, decimal.Decimal)):
                            vals.append(str(v))
                        elif isinstance(v, (bytes, bytearray)):
                            vals.append(str(int.from_bytes(v, 'big')))
                        elif isinstance(v, (datetime.datetime, datetime.date)):
                            vals.append("'%s'" % v)
                        else:
                            vals.append("'%s'" % str(v).replace('\\', '\\\\').replace("'", "\\'"))
                    f.write('INSERT INTO `%s` (%s) VALUES (%s);\n'
                            % (t, ','.join('`%s`' % c for c in cols), ','.join(vals)))
                f.write('\n')
    return path, snapshot


MEMBER_COLS = ['mobile', 'password', 'status', 'register_ip', 'register_terminal',
               'nickname', 'avatar', 'point', 'tag_ids', 'level_id', 'experience',
               'group_id', 'mark', 'creator', 'create_time', 'updater', 'update_time',
               'deleted', 'tenant_id']

ORDER_COLS = ['no', 'type', 'terminal', 'user_id', 'user_ip', 'status', 'product_count',
              'remark', 'comment_status', 'brokerage_user_id', 'pay_order_id', 'pay_status',
              'pay_time', 'pay_channel_code', 'finish_time', 'cancel_time', 'total_price',
              'discount_price', 'delivery_price', 'adjust_price', 'pay_price',
              'delivery_type', 'logistics_id', 'logistics_no', 'delivery_time',
              'receive_time', 'receiver_name', 'receiver_mobile', 'receiver_area_id',
              'receiver_detail_address', 'refund_status', 'refund_price', 'coupon_id',
              'coupon_price', 'use_point', 'point_price', 'give_point', 'refund_point',
              'vip_price', 'creator', 'create_time', 'updater', 'update_time',
              'deleted', 'tenant_id']

ITEM_COLS = ['user_id', 'order_id', 'cart_id', 'spu_id', 'spu_name', 'sku_id',
             'properties', 'pic_url', 'count', 'comment_status', 'price',
             'discount_price', 'delivery_price', 'adjust_price', 'pay_price',
             'coupon_price', 'point_price', 'use_point', 'give_point', 'vip_price',
             'after_sale_id', 'after_sale_status', 'creator', 'create_time', 'updater',
             'update_time', 'deleted', 'tenant_id']

DEFAULT_PROPERTIES = json.dumps(
    [{'propertyId': 0, 'propertyName': '默认', 'valueId': 0, 'valueName': '默认'}],
    ensure_ascii=False)


def insert_members(conn, members):
    """返回 {旧会员编号: 新 member_user.id}。"""
    sql = 'INSERT INTO member_user (%s) VALUES (%s)' % (
        ','.join('`%s`' % c for c in MEMBER_COLS),
        ','.join(['%s'] * len(MEMBER_COLS)))
    mapping = {}
    with conn.cursor() as cur:
        for m in members:
            cur.execute(sql, (
                m['mobile'], '', 0, '0.0.0.0', None, m['nickname'], '', m['point'],
                None, None, 0, None, m['mark'], '', m['create_time'], '', m['create_time'],
                0, TENANT_ID))
            mapping[m['legacy_id']] = cur.lastrowid
    return mapping


def insert_orders(conn, orders, items, member_ids):
    order_sql = 'INSERT INTO trade_order (%s) VALUES (%s)' % (
        ','.join('`%s`' % c for c in ORDER_COLS),
        ','.join(['%s'] * len(ORDER_COLS)))
    item_sql = 'INSERT INTO trade_order_item (%s) VALUES (%s)' % (
        ','.join('`%s`' % c for c in ITEM_COLS),
        ','.join(['%s'] * len(ITEM_COLS)))
    order_ids = []
    with conn.cursor() as cur:
        for o in orders:
            uid = member_ids[o['user_legacy_id']]
            cur.execute(order_sql, (
                o['no'], 0, o['terminal'], uid, '', o['status'], o['product_count'],
                o['remark'], 0, o['brokerage_user_id'], None, o['pay_status'],
                o['pay_time'], None, o['finish_time'], None, o['total_price'],
                o['discount_price'], 0, o['adjust_price'], o['pay_price'],
                o['delivery_type'], None, o['logistics_no'], None,
                None, o['receiver_name'], o['receiver_mobile'], None,
                None, o['refund_status'], o['refund_price'], None,
                0, 0, 0, 0, 0, 0, '', o['create_time'], '', o['create_time'],
                0, TENANT_ID))
            order_id = cur.lastrowid
            order_ids.append(order_id)
            for it in items:
                if it['order_no'] != o['no']:
                    continue
                cur.execute(item_sql, (
                    uid, order_id, None, it['spu_id'], it['spu_name'], it['sku_id'],
                    DEFAULT_PROPERTIES, it['pic_url'], it['count'], 0, it['price'],
                    0, 0, 0, it['pay_price'], 0, 0, 0, 0, 0, None, 0, '', o['create_time'],
                    '', o['create_time'], 0, TENANT_ID))
    return order_ids


# ══════════════════════════════════════════════════════════════════════════════
# 编排
# ══════════════════════════════════════════════════════════════════════════════

def collect(xlsx):
    sheets = load_sheets(xlsx)
    cats = build_categories(sheets['产品分类'])
    prods = build_products(sheets['产品列表'], cats)
    members, skipped = build_members(sheets['会员列表'])
    orders, items, unmatched, unknown = build_orders(
        sheets['订单列表'], sheets['流水列表'], members, prods)
    return dict(sheets=sheets, cats=cats, prods=prods, members=members, skipped=skipped,
                orders=orders, items=items, unmatched=unmatched, unknown=unknown)


def cmd_parse(data):
    print('解析结果')
    print('  分类 %d（一级 %d / 二级 %d）'
          % (len(data['cats']), sum(1 for c in data['cats'] if not c['parent_key']),
             sum(1 for c in data['cats'] if c['parent_key'])))
    print('  商品 %d（唯一编号 %d）'
          % (len(data['prods']), len({p['serial'] for p in data['prods']})))
    print('  会员 %d 可导入 / %d 跳过（无手机号）'
          % (len(data['members']), len(data['skipped'])))
    print('  订单 %d，明细 %d 行；其中 %d 行（%d 个名称）在商品表找不到对应'
          % (len(data['orders']), len(data['items']),
             sum(1 for i in data['items'] if not i['product_serial']), len(data['unmatched'])))
    print('  流水 %d 条（不入库，导出留档）' % len(data['sheets']['流水列表']))
    if data['unknown']:
        print('  ⚠️ 订单引用了会员表里没有的会员：%s' % data['unknown'])
    print('  状态分布：', end='')
    dist = {}
    for o in data['orders']:
        dist[o['status']] = dist.get(o['status'], 0) + 1
    print(dist)


def cmd_dry_run(data):
    """把将写入的每一条打出来。不连库、不连后端。"""
    cmd_parse(data)
    print('\n── 分类（%d）──' % len(data['cats']))
    for c in data['cats']:
        print('  %-8s %s%s' % ('[一级]' if not c['parent_key'] else '[二级]',
                               (c['parent_key'] + ' / ') if c['parent_key'] else '', c['name']))
    print('\n── 商品（前 5 / 共 %d）──' % len(data['prods']))
    for p in data['prods'][:5]:
        print('  %-16s %-42s %8.2f 元  库存 %-8d %s/%s'
              % (p['serial'], p['name'][:42], p['price'] / 100, p['stock'],
                 p['category_parent'], p['category_leaf']))
    print('\n── 会员（前 5 / 共 %d，跳过 %d）──' % (len(data['members']), len(data['skipped'])))
    for m in data['members'][:5]:
        print('  %-12s %-12s %s  %s' % (m['legacy_id'], m['mobile'], m['create_time'], m['nickname']))
    for m in data['skipped']:
        print('  [跳过] %-12s %s —— %s' % (m['legacy_id'], m['nickname'], m['reason']))
    print('\n── 订单（前 5 / 共 %d）──' % len(data['orders']))
    for o in data['orders'][:5]:
        print('  %s 旧状态%s→%d%s  原价 %8.2f  实付 %8.2f  商品 %s'
              % (o['no'], o['legacy_status'], o['status'],
                 '(全部退款)' if o['refund_status'] == 20 else '',
                 o['total_price'] / 100, o['pay_price'] / 100, o['receiver_name']))
    print('\n── 明细（前 5 / 共 %d）──' % len(data['items']))
    for i in data['items'][:5]:
        print('  %s  %-40s ×%-5d 单价 %8.2f  对应商品=%s'
              % (i['order_no'], i['spu_name'][:40], i['count'], i['price'] / 100,
                 i['product_serial'] or '（无）'))


def cmd_apply(data, yes):
    if data['unknown']:
        raise SystemExit('✗ 有订单引用了会员表里没有的会员，先解决：%s' % data['unknown'])
    if not yes:
        raise SystemExit('apply 会真实写入线上库。确认无误后加 --yes 再跑。')
    # 建分类/建商品这两步**不是幂等的**：重复跑会得到第二套 282 个商品。
    # 因此只要有上一次的成功 manifest，就先拒绝 —— 要么 rollback，要么手工改库。
    if MANIFEST.exists():
        old = json.loads(MANIFEST.read_text(encoding='utf-8'))
        if old.get('created'):
            raise SystemExit(
                '✗ 检测到上一次导入的 manifest（%s，已建 %d 个商品）。\n'
                '  重复 apply 会产生第二套商品。先回滚（rollback --yes）再跑，\n'
                '  或确认要保留现状时手工删掉 manifest。'
                % (old.get('finished_at', old.get('started_at')), len(old['created'].get('product_spu', []))))

    api = AdminApi()
    api.login()
    print('✓ admin-api 登录成功')

    conn = connect()
    print('✓ 数据库连接成功')

    backup, before = dump_tables(conn)
    print('✓ 已备份受影响表 → %s' % backup)

    manifest = {'started_at': datetime.datetime.now().isoformat(timespec='seconds'),
                'source': SOURCE_URL, 'tenant_id': TENANT_ID,
                'backup': str(backup),
                # 「被删掉的示例数据」一律取自导入前快照，它在删除**之前**采集，
                # 所以即使上一次跑崩在半路、示例数据已被逻辑删除，这份记录也不会漏。
                'deleted': {
                    'product_spu': [{'id': r['id'], 'name': r['name']}
                                    for r in before['product_spu']],
                    'product_category': [{'id': r['id'], 'name': r['name'],
                                          'parentId': r['parent_id']}
                                         for r in before['product_category']],
                },
                'created': {}}
    try:
        # ── 1. 清掉示例分类与示例商品 ──────────────────────────────────────
        # 商品先删（分类删不掉「已绑定 SPU」的分类）；商品删除是两步：先入回收站。
        existing_spus = api.spu_page()['list']
        for s_ in existing_spus:
            api.delete_spu(s_['id'])
        print('✓ 已删除示例商品 %d 个（快照中有 %d 个）'
              % (len(existing_spus), len(manifest['deleted']['product_spu'])))

        existing_cats = api.categories()
        # 先删二级再删一级：有子分类的删不掉（ProductCategoryServiceImpl.deleteCategory:71）
        for c in sorted(existing_cats, key=lambda c: 1 if c['parentId'] == 0 else 0):
            api.delete_category(c['id'])
        print('✓ 已删除示例分类 %d 个（含「探针分类」；快照中有 %d 个）'
              % (len(existing_cats), len(manifest['deleted']['product_category'])))
        # 破坏性的一步已经做完，先把 manifest 落盘 —— 后面任何一步崩掉都还能回滚
        MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding='utf-8')

        # ── 2. 建分类树 ──────────────────────────────────────────────────
        brand = next((b for b in api.brands() if b['name'] == BRAND_NAME), None)
        if not brand:
            raise SystemExit('✗ 租户 %d 下没有品牌「%s」' % (TENANT_ID, BRAND_NAME))
        print('✓ 复用品牌 %s id=%s' % (BRAND_NAME, brand['id']))

        # 幂等：同 (父, 名) 已存在就复用。**这一条很要紧** —— 上一次跑崩在建分类
        # 中途时，已建出来的那几个分类会留在库里，重跑若直接 create 就会建出第二套。
        existing_by_key = {(c['parentId'], c['name']): c['id'] for c in api.categories()}
        top_ids = {}
        cat_ids = {}
        manifest['created']['product_category'] = []
        for c in data['cats']:
            parent_id = 0 if not c['parent_key'] else top_ids[c['parent_key']]
            hit = existing_by_key.get((parent_id, c['name']))
            if hit is not None:
                cid, reused = hit, True
            else:
                cid, reused = api.create_category(
                    parent_id, c['name'], c['sort']), False
            if not c['parent_key']:
                top_ids[c['name']] = cid
            cat_ids[(c['parent_key'], c['name'])] = cid
            manifest['created']['product_category'].append(
                {'id': cid, 'name': c['name'], 'parent_key': c['parent_key'],
                 'reused': reused})
        n_reused = sum(1 for e in manifest['created']['product_category'] if e['reused'])
        print('✓ 分类就绪 %d 个（新建 %d，复用 %d）'
              % (len(cat_ids), len(cat_ids) - n_reused, n_reused))

        # ── 3. 建商品 ────────────────────────────────────────────────────
        manifest['created']['product_spu'] = []
        for idx, p in enumerate(data['prods']):
            cid = cat_ids[(p['category_parent'], p['category_leaf'])]
            spu_id = api.create_spu(p, cid, brand['id'])
            manifest['created']['product_spu'].append(
                {'idx': idx, 'id': spu_id, 'serial': p['serial'], 'name': p['name']})
            if (idx + 1) % 50 == 0:
                print('   … 已建 %d/%d' % (idx + 1, len(data['prods'])))
        print('✓ 已建商品 %d 个' % len(data['prods']))

        # ── 4. 回填销量与浏览量（admin-api 建单时会强制置 0）──────────────
        with conn.cursor() as cur:
            for entry in manifest['created']['product_spu']:
                p = data['prods'][entry['idx']]
                cur.execute('UPDATE product_spu SET sales_count=%s, browse_count=%s '
                            'WHERE id=%s AND tenant_id=%s',
                            (p['sales_count'], p['browse_count'], entry['id'], TENANT_ID))
        conn.commit()
        print('✓ 已回填销量/浏览量')

        # ── 5. 会员 ──────────────────────────────────────────────────────
        member_ids = insert_members(conn, data['members'])
        manifest['created']['member_user'] = [
            {'id': v, 'legacy_id': k} for k, v in member_ids.items()]
        print('✓ 已建会员 %d 个' % len(member_ids))

        # ── 6. 订单 ──────────────────────────────────────────────────────
        sku_ids = {}
        with conn.cursor() as cur:
            cur.execute('SELECT id, spu_id FROM product_sku WHERE tenant_id=%s', (TENANT_ID,))
            for sku_id, spu_id in cur.fetchall():
                sku_ids[spu_id] = sku_id
        spu_of_idx = {e['idx']: e['id'] for e in manifest['created']['product_spu']}
        for it in data['items']:
            if it['product_idx'] is not None:
                spu_id = spu_of_idx[it['product_idx']]
                it['spu_id'] = spu_id
                it['sku_id'] = sku_ids[spu_id]
            else:
                it['spu_id'] = it['sku_id'] = 0

        order_ids = insert_orders(conn, data['orders'], data['items'], member_ids)
        manifest['created']['trade_order'] = [
            {'id': i, 'no': o['no']} for i, o in zip(order_ids, data['orders'])]
        conn.commit()
        print('✓ 已建订单 %d 张、明细 %d 行' % (len(order_ids), len(data['items'])))
    finally:
        conn.close()

    manifest['finished_at'] = datetime.datetime.now().isoformat(timespec='seconds')
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding='utf-8')
    print('✓ manifest → %s' % MANIFEST)
    write_report(data, manifest)


def cmd_archive(xlsx, out):
    """流水表导出为留档 CSV（决定是不入库，但记录要留住）。

    用 utf-8-sig：Excel 双击打开中文不乱码。"""
    sheets = load_sheets(xlsx)
    rows = sheets['流水列表']
    header = list(rows[0].keys())
    with open(out, 'w', newline='', encoding='utf-8-sig') as f:
        w = csv.writer(f)
        w.writerow(header)
        for r in rows:
            w.writerow([s(r.get(h)) for h in header])
    print('✓ 流水留档 %d 条 → %s' % (len(rows), out))
    return 0


def cmd_verify(data):
    """读回核验：公开 app-api（不需鉴权）+ SQL。"""
    import urllib.request as u
    hdr = {'tenant-id': str(TENANT_ID)}
    def get(path):
        req = u.Request(ADMIN_BASE.replace('/admin-api', '/app-api') + path)
        for k, v in hdr.items():
            req.add_header(k, v)
        with u.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode('utf-8'))['data']

    cats = get('/product/category/list')
    page = get('/product/spu/page?pageNo=1&pageSize=1')
    print('公开接口读回：')
    print('  分类 %d 个（期望 %d 个一级+二级）' % (len(cats), len(data['cats'])))
    print('  商品总数 %s（期望 %d）' % (page['total'], len(data['prods'])))

    conn = connect()
    with conn.cursor() as cur:
        cur.execute('SELECT COUNT(*) FROM member_user WHERE tenant_id=%s AND mark LIKE %s',
                    (TENANT_ID, '%旧商城%'))
        n_mem = cur.fetchone()[0]
        cur.execute('SELECT COUNT(*), SUM(pay_price) FROM trade_order WHERE tenant_id=%s AND remark LIKE %s',
                    (TENANT_ID, '%旧商城%'))
        n_ord, sum_pay = cur.fetchone()
        cur.execute('SELECT COUNT(*) FROM trade_order_item i JOIN trade_order o ON i.order_id=o.id '
                    'WHERE o.tenant_id=%s AND o.remark LIKE %s', (TENANT_ID, '%旧商城%'))
        n_item = cur.fetchone()[0]
        cur.execute('SELECT status, COUNT(*) FROM trade_order WHERE tenant_id=%s AND remark LIKE %s GROUP BY status',
                    (TENANT_ID, '%旧商城%'))
        dist = dict(cur.fetchall())
    conn.close()
    expect_pay = sum(o['pay_price'] for o in data['orders'])
    print('  会员 %d 人（期望 %d）' % (n_mem, len(data['members'])))
    print('  订单 %d 张（期望 %d）状态分布 %s' % (n_ord, len(data['orders']), dist))
    print('  明细 %d 行（期望 %d）' % (n_item, len(data['items'])))
    print('  实付合计 %.2f 元（期望 %.2f）' % ((sum_pay or 0) / 100, expect_pay / 100))
    ok = (len(cats) == len(data['cats']) and page['total'] == len(data['prods'])
          and n_mem == len(data['members']) and n_ord == len(data['orders'])
          and n_item == len(data['items']) and (sum_pay or 0) == expect_pay)
    print('\n%s' % ('✅ 核验通过' if ok else '❌ 核验不通过 —— 逐项比对上面的数字'))
    return ok


def cmd_rollback(yes):
    if not yes:
        raise SystemExit('rollback 会删数据。确认后加 --yes。')
    if not MANIFEST.exists():
        raise SystemExit('✗ 没有 manifest，无法按 id 精确回滚')
    mf = json.loads(MANIFEST.read_text(encoding='utf-8'))
    conn = connect()
    try:
        with conn.cursor() as cur:
            order_ids = [o['id'] for o in mf['created'].get('trade_order', [])]
            if order_ids:
                fmt = ','.join(['%s'] * len(order_ids))
                cur.execute('DELETE FROM trade_order_item WHERE order_id IN (%s)' % fmt, order_ids)
                cur.execute('DELETE FROM trade_order WHERE id IN (%s)' % fmt, order_ids)
            mem_ids = [o['id'] for o in mf['created'].get('member_user', [])]
            if mem_ids:
                cur.execute('DELETE FROM member_user WHERE id IN (%s)'
                            % ','.join(['%s'] * len(mem_ids)), mem_ids)
            # 本次建的商品/分类：物理删除（它们从没被外部引用过）
            for entry in mf['created'].get('product_spu', []):
                cur.execute('DELETE FROM product_sku WHERE spu_id=%s', (entry['id'],))
                cur.execute('DELETE FROM product_spu WHERE id=%s', (entry['id'],))
            for entry in mf['created'].get('product_category', []):
                cur.execute('DELETE FROM product_category WHERE id=%s', (entry['id'],))
            # 被删掉的示例数据要**取消逻辑删除**，不能重新 INSERT：
            # yudao 的 BaseDO.deleted 带 @TableLogic，DELETE 接口只是把它置 1，行还在。
            restored = {}
            for table in ('product_spu', 'product_category'):
                ids = [e['id'] for e in mf['deleted'].get(table, [])]
                if not ids:
                    continue
                cur.execute('UPDATE %s SET deleted=0 WHERE id IN (%s)'
                            % (table, ','.join(['%s'] * len(ids))), ids)
                restored[table] = cur.rowcount
        conn.commit()
        print('✓ 已删除：订单 %d、会员 %d、商品 %d、分类 %d'
              % (len(order_ids), len(mem_ids), len(mf['created'].get('product_spu', [])),
                 len(mf['created'].get('product_category', []))))
        print('✓ 已恢复示例数据（取消逻辑删除）：%s' % restored)
    finally:
        conn.close()


def write_report(data, manifest):
    lines = ['# 旧商城数据导入报告', '',
             '生成时间：%s' % datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
             '来源：`%s`　目标：租户 %d（御旺宸发）' % (SOURCE_URL, TENANT_ID),
             '导入前备份：`%s`' % manifest['backup'], '',
             '## 写入条数', '',
             '| 对象 | 条数 |', '|---|---|',
             '| 商品分类 | %d |' % len(manifest['created'].get('product_category', [])),
             '| 商品 SPU | %d |' % len(manifest['created'].get('product_spu', [])),
             '| 会员 | %d |' % len(manifest['created'].get('member_user', [])),
             '| 订单 | %d |' % len(manifest['created'].get('trade_order', [])),
             '| 订单明细 | %d |' % len(data['items']),
             '| 已删除的示例分类 | %d |' % len(manifest['deleted'].get('product_category', [])),
             '| 已删除的示例商品 | %d |' % len(manifest['deleted'].get('product_spu', [])),
             '', '## 已知缺口（不是缺陷，是旧系统没导出）', '',
             '① 282 个商品均无主图，统一用 `%s`；' % PIC_URL,
             '② 商品简介与详情为 `[[ ]]` 占位符 —— 旧系统未导出，不得编造；',
             '③ 订单无收货地址、无发货/收货时间、无物流公司；',
             '④ 5 个无手机号会员未导入（见下）。', '']
    if data['unmatched']:
        recoverable, gone = classify_unmatched(data['unmatched'], data['prods'])
        lines += ['## 订单明细里在商品表找不到对应商品的名称（%d 个）' % len(data['unmatched']), '',
                  '这些明细行的 `spu_id`/`sku_id` 记 0、单价记 0；订单金额不受影响（取旧系统原值）。',
                  '',
                  '- **%d 个「可恢复」**：去掉尾部【规格】后能对上商品表的父商品（旧系统的'
                  '「多规格」商品，订单里带规格后缀、商品表只导出了父名）。**没有自动关联** ——'
                  '父商品的价不等于被下单那个规格的价，硬联等于给历史订单编单价。'
                  % len(recoverable),
                  '- **%d 个「不可恢复」**：旧系统里确实已无对应商品。' % len(gone), '',
                  '### 不可恢复的（%d）' % len(gone), '']
        lines += ['- %s' % n for n in gone] + ['']
        lines += ['### 可恢复的（%d，未关联）' % len(recoverable), '']
        lines += ['- %s' % n for n in recoverable] + ['']
    lines += ['## 未导入的会员（无手机号，%d 人）' % len(data['skipped']), '']
    lines += ['| 旧会员编号 | 昵称 | 原因 |', '|---|---|---|']
    lines += ['| %s | %s | %s |' % (m['legacy_id'], m['nickname'], m['reason'])
              for m in data['skipped']]
    lines += ['', '## 改动过的原值', '',
              '- `DZ001 不干胶定制` 库存 `%d`（旧系统的"无限库存"哨兵）→ `%d`。'
              % (STOCK_SENTINEL, STOCK_SENTINEL_REPLACEMENT)]
    REPORT.write_text('\n'.join(lines), encoding='utf-8')
    print('✓ 报告 → %s' % REPORT)


def main(argv=None):
    ap = argparse.ArgumentParser(description='旧商城数据导入')
    ap.add_argument('command', choices=['parse', 'dry-run', 'archive', 'backup',
                                        'apply', 'verify', 'rollback'])
    ap.add_argument('xlsx', nargs='?', default=XLSX)
    ap.add_argument('--yes', action='store_true', help='确认执行有破坏性的动作')
    ap.add_argument('--out', default=str(SCRIPTS / 'legacy-mall-finance-archive.csv'),
                    help='archive 的输出路径')
    args = ap.parse_args(argv)

    if args.command == 'archive':
        return cmd_archive(args.xlsx, args.out)
    if args.command in ('parse', 'dry-run', 'apply', 'verify'):
        data = collect(args.xlsx)
    if args.command == 'parse':
        cmd_parse(data)
    elif args.command == 'dry-run':
        cmd_dry_run(data)
    elif args.command == 'apply':
        cmd_apply(data, args.yes)
    elif args.command == 'verify':
        return 0 if cmd_verify(data) else 1
    elif args.command == 'backup':
        conn = connect()
        try:
            path, snap = dump_tables(conn)
            print('✓ 备份 → %s' % path)
            for t, rows in snap.items():
                print('    %-18s %d 行' % (t, len(rows)))
        finally:
            conn.close()
    elif args.command == 'rollback':
        cmd_rollback(args.yes)
    return 0


if __name__ == '__main__':
    sys.exit(main())
