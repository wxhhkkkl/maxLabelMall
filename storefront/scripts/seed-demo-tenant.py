"""
在租户 162（御旺宸发）下建最小可用数据。**幂等**：已存在的按名字复用，可重复跑。

机制：`tenant-id: 1` 鉴权（超管属于租户 1）+ `visit-tenant-id: 162` 切数据上下文
     —— yudao 官方的"超管切租户"通道。

⚠️ 商品必须挂在**二级及以下**分类（yudao 校验：必须使用第二级的商品分类及以下），
   所以分类是两层。
"""
import json
import urllib.request

BASE = 'http://localhost:48080/admin-api'
PIC = '/assets/logo.png'          # 封面：设计稿的 logo
# 图集用的示例图（`storefront/public/assets/sample-*.svg`）—— 800×800 浅蓝底 +
# 大号序号 + 「示例图 N」，**一眼可辨不是真实产品图**。有 3 张不同的图，
# 「点缩略图切换主图」这个功能才看得出来。
GALLERY = ['/assets/sample-1.svg', '/assets/sample-2.svg', '/assets/sample-3.svg']

# 示例详情富文本：**只演示排版能力，不编造产品参数**。
# 真实商品请到后台「商品管理 → 商品详情」替换。
DESCRIPTION = (
    '<h3>这是示例商品详情</h3>'
    '<p>本段用于演示详情区的富文本排版：标题、段落、列表、图片与表格都能正常渲染，'
    '并保留后台配置的样式。请到后台「商品管理 → 商品详情」把它替换为真实内容。</p>'
    '<ul>'
    '<li>列表样式：就像这样</li>'
    '<li>图集在主图下方，点缩略图可以切换主图</li>'
    '<li>富文本渲染前会做安全过滤，脚本与 on* 事件属性会被剔除</li>'
    '</ul>'
    '<p>以上均为示例文案，<strong>不含任何真实产品参数</strong>。</p>'
)


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header('tenant-id', '1')
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
        req.add_header('visit-tenant-id', '162')
    data = None
    if body is not None:
        req.add_header('Content-Type', 'application/json; charset=utf-8')
        data = json.dumps(body, ensure_ascii=False).encode('utf-8')
    with urllib.request.urlopen(req, data) as r:
        return json.loads(r.read().decode('utf-8'))


def need(res, what):
    if res.get('code') != 0:
        raise SystemExit('✗ %s 失败: %s' % (what, res.get('msg')))
    return res.get('data')


token = need(call('POST', '/system/auth/login',
                  {'username': 'admin', 'password': 'admin123'}), '登录')['accessToken']
print('✓ 登录成功')

# ---- 品牌（幂等） ----
brands = need(call('GET', '/product/brand/list', token=token), '读品牌') or []
brand = next((b for b in brands if b['name'] == '御旺宸发'), None)
if brand:
    brand_id = brand['id']
    print('· 品牌已存在 id=%s' % brand_id)
else:
    brand_id = need(call('POST', '/product/brand/create', {
        'name': '御旺宸发', 'picUrl': PIC, 'sort': 1, 'status': 0,
        'description': '本租户商品品牌',
    }, token=token), '建品牌')
    print('✓ 品牌 id=%s' % brand_id)


# ---- 分类（两层，幂等） ----
def ensure_category(name, parent_id):
    cats = need(call('GET', '/product/category/list', token=token), '读分类') or []
    hit = next((c for c in cats if c['name'] == name and c['parentId'] == parent_id), None)
    if hit:
        return hit['id']
    return need(call('POST', '/product/category/create', {
        'parentId': parent_id, 'name': name, 'picUrl': PIC, 'sort': 1, 'status': 0,
    }, token=token), '建分类 ' + name)


# 一级用设计稿 www/mall.html 里那 5 个分类名；二级按打印耗材行当的常规切分
TREE = {
    '标签耗材': ['热敏标签', '铜版/合成标签'],
    '碳带色带': ['蜡基碳带', '混合基碳带'],
    '打印设备': ['桌面打印机', '工业打印机'],
    '扫描与喷码': ['扫码设备', '喷码设备'],
    '软件服务': ['标签编辑软件'],
}
leaf_ids = {}
for top, children in TREE.items():
    top_id = ensure_category(top, 0)
    print('✓ 一级分类 %s id=%s' % (top, top_id))
    for child in children:
        leaf_ids[child] = ensure_category(child, top_id)
        print('   ✓ 二级分类 %s id=%s' % (child, leaf_ids[child]))

# ---- 商品：名称带【示例】前缀，一眼可辨是占位数据 ----
PRODUCTS = [
    ('【示例】热敏标签纸 40×30mm', '热敏标签', 2900, 200),
    ('【示例】铜版纸标签 60×40mm', '铜版/合成标签', 3900, 200),
    ('【示例】蜡基碳带 110mm×300m', '蜡基碳带', 4500, 100),
    ('【示例】热敏标签打印机 TP-200', '桌面打印机', 39900, 50),
    ('【示例】无线扫码枪 SR-10', '扫码设备', 18900, 30),
]

# 已存在的按名字取出 id。本脚本**幂等且收敛**：已存在就**更新** ——
# 早期版本是「已存在就跳过」，于是改了示例文案/图集后重跑不会生效。
existing = {
    p['name']: p['id']
    for p in (need(call('GET', '/product/spu/page?pageNo=1&pageSize=100', token=token), '读商品')['list'] or [])
}

for name, leaf, price, stock in PRODUCTS:
    body = {
        'name': name,
        'keyword': name,
        'introduction': '示例商品，请在后台替换为真实商品信息',
        'description': DESCRIPTION,
        'categoryId': leaf_ids[leaf],
        'brandId': brand_id,
        'picUrl': GALLERY[0],
        'sliderPicUrls': GALLERY,
        'sort': 1,
        'specType': False,        # 单规格：前台不渲染规格表
        'deliveryTypes': [1],     # 1 = 快递
        'giveIntegral': 0,
        'subCommissionType': False,
        'skus': [{
            # marketPrice 刻意留 0：等于售价会让前台显示成「¥45.00 ¥45.00」
            # （现价 + 一模一样的划线价）。也不编一个假原价去凑折扣。
            'name': name, 'price': price, 'marketPrice': 0,
            'picUrl': GALLERY[0], 'stock': stock, 'weight': 0.2,
        }],
    }
    if name in existing:
        need(call('PUT', '/product/spu/update', dict(body, id=existing[name]), token=token),
             '更新商品 ' + name)
        print('OK 已更新 %s id=%s 价 CNY%.2f 库存 %d' % (name, existing[name], price / 100, stock))
    else:
        spu_id = need(call('POST', '/product/spu/create', body, token=token), '建商品 ' + name)
        print('OK 新建 %s id=%s 价 CNY%.2f 库存 %d' % (name, spu_id, price / 100, stock))

print()
print('完成。')
