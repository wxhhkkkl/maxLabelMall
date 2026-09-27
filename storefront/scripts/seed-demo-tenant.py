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
PIC = '/assets/logo.png'   # 设计稿里唯一的图片资产就是 logo；示例数据沿用它


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

existing = {p['name'] for p in (need(
    call('GET', '/product/spu/page?pageNo=1&pageSize=100', token=token), '读商品')['list'] or [])}

for name, leaf, price, stock in PRODUCTS:
    if name in existing:
        print('· 商品已存在，跳过：%s' % name)
        continue
    spu_id = need(call('POST', '/product/spu/create', {
        'name': name, 'keyword': name,
        'introduction': '示例商品，请在后台替换为真实商品信息',
        'description': '<p>示例商品详情，请在后台替换。</p>',
        'categoryId': leaf_ids[leaf],
        'brandId': brand_id,
        'picUrl': PIC, 'sliderPicUrls': [PIC],
        'sort': 1,
        'specType': False,        # 单规格：前台不渲染规格表
        'deliveryTypes': [1],     # 1 = 快递
        'giveIntegral': 0,
        'subCommissionType': False,
        'skus': [{
            'name': name, 'price': price, 'marketPrice': price,
            'picUrl': PIC, 'stock': stock, 'weight': 0.2,
        }],
    }, token=token), '建商品 ' + name)
    print('✓ 商品 %s id=%s 价 ¥%.2f 库存 %d' % (name, spu_id, price / 100, stock))

print('\n完成。')
