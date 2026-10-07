"""旧商城全量数据抓取。

把 u247521.yz168.cc（域名 yuwangchenfa.com，SiteID 88043）后台的数据与图片抓到
`oldMallData/`，保留旧主键，供之后导入新库。

设计要点：
- **断点续抓**：原始接口响应先落 `.cache/raw/<key>.json`，重跑时直接复用。
- **会话失效即停**：检测到登录页/`success=false` 就停下并提示重新登录，已抓部分不丢。
- **图片前缀探测**：字段里常只有裸文件名，按候选前缀逐个试，命中后记住该前缀。
- **不编造**：抓不到就记进 manifest 的 failures，不填占位值。

跑法：
    python scripts/scrape_old_mall.py            # 全量抓取（会话须有效）
    python scripts/scrape_old_mall.py --images   # 只补图片
"""
import hashlib
import html as html_mod
import json
import re
import sys
import time
import urllib.parse
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

OUT = ROOT / "oldMallData"
CACHE = OUT / ".cache"
RAW = CACHE / "raw"
IMAGES = OUT / "images"
DETAILS = OUT / "product_details"
DATA = OUT / "data"

CDN = "https://img.wds168.cn"
SITE_ID = "88043"
STOREFRONT = "https://yuwangchenfa.com/"
REQUEST_GAP = 0.4
RETRIES = 3

# 支付/邮件/短信凭据：对「导入新库」零价值，且是活凭据，不随数据落盘。
#
# 光列固定名字不够 —— 一处漏了就会把活密钥写进快照（实测 siteconfig 的 UserInfo
# 里有 SMSPassword / AlipayPrivateKey / UnionPaySignPasswd / BaiduLbsKey / APIPEMKeyPath，
# 还有 BmapKey / QmapKey 这类地图 API 密钥，都是先前没列到的）。
# 所以除了片段匹配，还统一剔掉「Key 结尾」的字段 —— 支付宝公钥也一并去掉：
# 它本非机密，但对导入新库毫无用处，不值得为它留一条特例。
SECRET_KEYS = {
    "WxPayAppSecret", "WxPayKey", "AlipayKey", "EpaylinksKey",
    "SmtpPass", "SmtpUser", "SN", "APICertPath",
    # 实测 siteconfig 的 UserInfo 里还有这些，先前只列固定名字时漏掉了
    "SMSPass", "SMSPassword", "BmapKey", "QmapKey", "BaiduLbsKey",
    "APIPEMKeyPath", "UnionPaySignPasswd", "AlipayPrivateKey",
}
SECRET_NAME_FRAGMENTS = (
    "secret", "passw", "privatekey", "pemkey", "lbskey",
    "signkey", "accesskey", "appkey", "apikey",
)


def is_secret_key(name):
    """字段名是否属于凭据。

    ⚠️ 别用宽泛的 `pass` / `key` 片段：数据里有 `ArtKeyword`/`ProKeyword`/
    `SeoKeyword`/`TagKeyword`（SEO 关键词）和 `EnableSmsFindPass`（开关），
    按片段匹配会把这些正常字段一起删掉。所以「Key 结尾」要显式排除 `Keyword`。
    """
    if not isinstance(name, str):
        return False
    if name in SECRET_KEYS:
        return True
    low = name.lower()
    if any(f in low for f in SECRET_NAME_FRAGMENTS):
        return True
    return low.endswith("key") and not low.endswith("keyword")


# --------------------------------------------------------------------------
# 纯函数（见 scripts/test_scrape_old_mall.py）
# --------------------------------------------------------------------------

def repair_image_name(name):
    """修复源数据里丢掉路径分隔符的值。

    实测遇到：`comdata88043product20250523151113039AA58F27F393D3_s.jpg`
    （旧系统某处写入时把 `/` 吃掉了）。补回斜杠后 CDN 上是 200。
    只在能唯一还原时修，否则原样返回 —— 不猜。
    """
    m = re.match(r"^comdata(\d+)(product|news|icontemp|images)(.+)$", name or "")
    if m:
        return f"/comdata/{m.group(1)}/{m.group(2)}/{m.group(3)}"
    return name


def img_candidates(name, hint=None):
    """把字段里的图片标识解析成**有序**候选绝对 URL。

    标识有三种形态：裸文件名、站点绝对路径（/comdata/...）、完整 URL。
    裸文件名不知道落在哪个目录，只能按候选前缀逐个试探。
    """
    if not name or not isinstance(name, str):
        return []
    name = repair_image_name(name.strip())
    if not name:
        return []
    if name.startswith("//"):
        return ["https:" + name]
    if name.startswith("http://") or name.startswith("https://"):
        return [name]
    if name.startswith("/"):
        return [CDN + name]

    cands = []
    if hint:
        cands.append(f"{CDN}/comdata/{SITE_ID}/{hint.strip('/')}/{name}")
    cands.append(f"{CDN}/comdata/{SITE_ID}/product/{name}")
    ym = name[:6]
    if ym.isdigit():
        cands.append(f"{CDN}/comdata/{SITE_ID}/{ym}/{name}")
    cands.append(f"{CDN}/comdata/{SITE_ID}/news/{name}")
    if ym.isdigit():
        cands.append(f"{CDN}/comdata/{SITE_ID}/icontemp/{ym}/{name}")

    seen, out = set(), []
    for c in cands:
        if c not in seen:
            seen.add(c)
            out.append(c)
    return out


def split_image_field(value):
    """图片字段可能是**逗号分隔的多张图**（实测 BigImages 形如 "a_b.png,b_b.png"）。

    当成单个文件名去取会全部 404 —— 这里按逗号/换行拆开，逐张处理。
    """
    if not isinstance(value, str):
        return []
    return [part.strip() for part in re.split(r"[,;\r\n]+", value) if part.strip()]


def local_img_path(src, kind=None):
    """图片落盘路径（相对 oldMallData/）。

    product 桶只放主图（基线文件名唯一）；其余进 content 桶并保留月份子目录，
    避免不同月份的同类文件名互相覆盖。
    """
    p = src.split("?")[0]
    p = re.sub(r"^https?://[^/]+", "", p)
    base = p.rsplit("/", 1)[-1]
    if kind == "product" or (kind is None and "/product/" in p):
        return f"images/product/{base}"
    m = re.match(r"^/comdata/\d+/(.+)$", p)
    sub = m.group(1) if m else base
    if kind == "article":
        return f"images/article/{base}"
    return f"images/content/{sub}"


def detail_rel_path(local_path):
    """详情页里引用图片的相对路径。

    详情页在 `oldMallData/product_details/<id>.html`，图片在 `oldMallData/images/…`，
    同一层再进一个子目录 —— 只需**一层** `../`。
    """
    return "../" + local_path.lstrip("/")


IMG_SRC_RE = re.compile(r'(<img\b[^>]*?\bsrc\s*=\s*)(["\'])(.*?)\2', re.I | re.S)


def extract_img_srcs(html):
    """正文 HTML 里所有 <img> 的 src。"""
    return [m.group(3) for m in IMG_SRC_RE.finditer(html or "")]


def replace_img_srcs(html, mapping):
    """按 mapping 改写 <img src>；未命中的原样保留。"""
    if not html:
        return html or ""

    def sub(m):
        new = mapping.get(m.group(3))
        if not new:
            return m.group(0)
        return m.group(1) + m.group(2) + new + m.group(2)

    return IMG_SRC_RE.sub(sub, html)


def resolve_category_path(class_id, categories):
    """由 ClassID 还原「一级 / 二级」名称（旧库最多两层）。"""
    by_id = {str(c.get("ClassID")): c for c in categories or []}
    node = by_id.get(str(class_id))
    if node is None:
        return {"level1": None, "level2": None}
    parent_id = str(node.get("ParentID") or "0")
    if parent_id in ("0", "", "None"):
        return {"level1": node.get("Name"), "level2": None}
    parent = by_id.get(parent_id)
    return {"level1": parent.get("Name") if parent else None, "level2": node.get("Name")}


def split_order_goods(order):
    """把订单内嵌的 Goods 拍平成明细行，带上 OrderID 外键。"""
    rows = []
    for g in order.get("Goods") or []:
        rows.append({
            "OrderID": order.get("OrderID"),
            "ItemID": g.get("ItemID"),
            "ProductID": g.get("ProductID"),
            "ProductName": g.get("ProductName"),
            "Price": g.get("Price"),
            "Amount": g.get("Amount"),
            "MarketPrice": g.get("MarketPrice"),
            "OriginPrice": g.get("OriginPrice"),
            "Image": g.get("Image"),
            "ProNorms": g.get("ProNorms"),
            "ProductAttrs": g.get("ProductAttrs"),
            "CrTime": g.get("CrTime"),
        })
    return rows


# 知情保留：会员 `Password` 是旧系统的 MD5 哈希。所有者选的是「下载但排除版本库」，
# 明确包含「密码哈希」在内；新系统用不上它（C 端为手机号+短信验证码登录），
# 保留仅为数据完整。**别把它当漏网之鱼顺手删掉。**
KEEP_KEYS = {"Password"}


def strip_secrets(d):
    """递归剔除凭据字段（凭据可能藏在嵌套的 UserInfo 里），KEEP_KEYS 除外。"""
    if isinstance(d, dict):
        return {k: (strip_secrets(v) if isinstance(v, (dict, list)) else v)
                for k, v in d.items()
                if k in KEEP_KEYS or not is_secret_key(k)}
    if isinstance(d, list):
        return [strip_secrets(v) if isinstance(v, (dict, list)) else v for v in d]
    return {}


SITE_HOST = "https://yuwangchenfa.com"

# 站点页面之外的前台 URL：商品/文章/分类另有专门的抓取路径，不进 site_pages
NON_PAGE_PREFIXES = ("/ProductDetail/", "/NewsDetail/", "/Product/",
                     "/NewsList/", "/CouponList/", "/ProductIndex")


def classify_site_url(url):
    """把 sitemap 里的 URL 归类。

    返回 "content"（/Content/<id>.html 单页）、"landing"（命名落地页）、
    "download"（/DownLoad/…），或 None 表示「不是站点页面，另有抓取路径」。
    """
    if not url or not isinstance(url, str):
        return None
    path = url.replace(SITE_HOST, "").strip()
    if path in ("", "/"):
        return None
    if path.startswith("/Content/"):
        return "content"
    if path.startswith("/DownLoad/") or path.startswith("/DownList/"):
        return "download"
    for p in NON_PAGE_PREFIXES:
        if path.startswith(p):
            return None
    return "landing"


def page_slug(kind, url):
    """给站点页面取一个稳定的文件名（不含扩展名）。"""
    path = url.replace(SITE_HOST, "").strip("/")
    m = re.match(r"Content/(\d+)\.html$", path)
    if m:
        return f"{kind}_{m.group(1)}"
    slug = re.sub(r"[^A-Za-z0-9]+", "_", path).strip("_").lower()
    return f"{kind}_{slug}" if slug else kind


def is_localizable_img(src):
    """只本地化站点自己的素材（/comdata/），不动 static.wds168.cn 的 CSS/图标。"""
    return isinstance(src, str) and "/comdata/" in src


def html_to_text(html):
    """剥标签取纯文本，便于检索与比对。"""
    if not html:
        return ""
    body = re.sub(r"<(script|style)\b[^>]*>.*?</\1>", " ", html, flags=re.S | re.I)
    txt = re.sub(r"<[^>]+>", " ", body)
    txt = html_mod.unescape(txt)
    return re.sub(r"[ \t ]+", " ", txt).strip()


def unwrap_setting(resp):
    """取配置类接口的载荷。

    位置不统一：有的塞在 `info`，有的在 `data`，有的直接给 `list`。
    都没有时退回去掉框架字段后的顶层 —— 尤其要丢掉 `cookies`，
    它每次请求都换、还带着 CSRF token，存进去纯属噪音。
    """
    if not isinstance(resp, dict):
        return resp
    for key in ("info", "data", "list"):
        if key in resp:
            return resp[key]
    return {k: v for k, v in resp.items()
            if k not in ("success", "msg", "cookies")}


def is_blank_form_row(row):
    """判断表单记录是不是空占位。

    旧系统对「有定义但没人提交」的表单也会返回记录：整行只有
    `x_empty="nodata"`，没有任何 `cc_` 开头的实际字段。实测 86 条这样的行，
    都不是真实提交，不该混进数据里。
    """
    if not isinstance(row, dict):
        return True
    for k, v in row.items():
        if not k.startswith("cc_"):
            continue
        s = str(v).strip()
        if s and s.lower() not in ("nodata", "none", "null"):
            return False
    return True


def duplicate_ids(rows, key):
    """返回重复出现的主键。

    用途：分页参数写错时接口会**一直返回第 1 页**，行数看着对、主键全是重复的。
    抓完每张表都过一遍这个检查，比事后数条数可靠。
    """
    seen, dups = set(), []
    for r in rows or []:
        v = r.get(key)
        if v in seen:
            dups.append(v)
        else:
            seen.add(v)
    return dups


# --------------------------------------------------------------------------
# 网络层
# --------------------------------------------------------------------------

class SessionExpired(RuntimeError):
    pass


class OldMall:
    def __init__(self, session, base, admin_url):
        self.s = session
        self.base = base
        self.referer = admin_url
        self.failures = []
        # 订单在下载图片阶段还要再扫一遍明细里的商品图，抓完由 main 填
        self.orders = []

    def _request(self, url, data=None, stream=False, referer=None, method="post"):
        last = None
        for attempt in range(RETRIES):
            try:
                fn = self.s.get if method == "get" else self.s.post
                r = fn(url, data=data, timeout=60, stream=stream,
                       headers={"Referer": referer or self.referer})
                if r.status_code >= 500 and attempt < RETRIES - 1:
                    time.sleep(1.5 ** attempt)
                    continue
                return r
            except requests.RequestException as e:
                last = e
                time.sleep(1.5 ** attempt)
        raise RuntimeError(f"请求失败 {url}: {last}")

    def json(self, path, data=None, referer=None, method="post"):
        """多数接口吃 POST 表单；个别接口（如 GetProductInfo）只从查询串取参，必须 GET。"""
        data = data or {}
        url = self.base + path
        if method == "get":
            if data:
                url += ("&" if "?" in url else "?") + urllib.parse.urlencode(data)
            r = self._request(url, referer=referer, method="get")
        else:
            r = self._request(url, data=data, referer=referer)
        text = r.text
        if "<html" in text[:200].lower():
            raise SessionExpired(f"返回 HTML 而非 JSON，会话可能已过期：{path}")
        try:
            j = r.json()
        except ValueError:
            raise SessionExpired(f"响应不是 JSON，会话可能已过期：{path}")
        if j.get("success") is False:
            raise SessionExpired(f"接口返回 success=false：{path} {j.get('msg')}")
        return j

    def cached(self, key, producer, force=False):
        """原始响应落盘复用。key 决定缓存文件名。"""
        f = RAW / f"{key}.json"
        if f.exists() and not force:
            return json.loads(f.read_text(encoding="utf-8"))
        time.sleep(REQUEST_GAP)
        val = producer()
        RAW.mkdir(parents=True, exist_ok=True)
        f.write_text(json.dumps(val, ensure_ascii=False), encoding="utf-8")
        return val

    def paginate(self, key, path, page_param="page", extra=None, force=False, method="post"):
        """按页拉全，返回合并后的 list。

        ⚠️ 各模块的分页参数名与请求方法**都不一样**，实测结论（写死在这里，别再猜）：
          商品 GetList   POST  curPage
          会员 Usermanage POST  page
          订单 order…List GET   page   ← POST 会一直返回第 1 页
          文章 getlist   POST  page
        「总数」字段名同样不一致：商品/订单/文章给 totalcount，会员给 rowcount，
        优惠券给 count —— 取到就收，取不到靠 pagecount / 空页收尾。
        """
        extra = dict(extra or {})
        pages = []

        def produce():
            n, total, pagecount = 1, None, None
            while True:
                data = dict(extra, **{page_param: n})
                j = self.json(path, data, method=method)
                rows = j.get("list") or []
                if total is None:
                    total = int(j.get("totalcount") or j.get("count")
                                or j.get("rowcount") or 0)
                    pagecount = int(j.get("pagecount") or 0)
                    print(f"    {key}: 声明 {total} 条 / {pagecount} 页")
                pages.append((n, rows))
                if not rows:
                    break
                if total and sum(len(r) for _, r in pages) >= total:
                    break
                if pagecount and n >= pagecount:
                    break
                if n >= 500:
                    break
                n += 1
            return {"pages": pages}

        bundles = self.cached(key, produce, force=force)
        rows = []
        for _, chunk in bundles["pages"]:
            rows.extend(chunk)
        print(f"    {key}: 实得 {len(rows)} 条")
        return rows

    def download(self, url, dest, hint=None):
        dest = Path(dest)
        if dest.exists() and dest.stat().st_size > 0:
            return True
        r = self._request(url, stream=True, referer=STOREFRONT, method="get")
        ct = r.headers.get("content-type", "")
        if r.status_code != 200 or not ct.startswith("image/") or not r.content:
            return False
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(r.content)
        return True


def resolve_and_download(mall, ident, kind=None, hint=None):
    """裸文件名按候选前缀逐个试，命中即下载。返回落盘相对路径或 None。"""
    for url in img_candidates(ident, hint=hint):
        rel = local_img_path(url, kind)
        if mall.download(url, OUT / rel):
            return rel
    return None


# --------------------------------------------------------------------------
# 各表抓取
# --------------------------------------------------------------------------

PRODUCT_LIST = "/index.php?c=Admin/ShopAdmin/Product/Productmanage&a=GetList"
PRODUCT_INFO = "/index.php?c=Admin/ShopAdmin/product/productmanage&a=GetProductInfo&sid=&id={id}&_=1"
CATEGORY_LIST = "/index.php?c=Admin/ShopAdmin/Product/productmanage&a=getclasslist"
MEMBER_LIST = "/index.php?c=admin/ShopAdmin/user/usermanage&a=GetList"
ORDER_LIST = "/index.php?c=Admin/ShopAdmin/order/order&a=orderAdministrationList"
NEWS_LIST = "/index.php?c=Admin/ShopAdmin/News/News&a=getlist"
NEWS_INFO = "/index.php?c=Admin/ShopAdmin/News/News&a=GetNewsInfo&sid=&id={id}"
NEWS_CLASS = "/index.php?c=Admin/ShopAdmin/News/News&a=getclasslist"
COUPON_LIST = "/index.php?c=admin/ShopAdmin/Marketing/Coupon&a=GetList"
FREIGHT = "/index.php?c=Admin/ShopAdmin/ShopConfig/shopconfig&a=getFreightTemplateList"
SHOPCONFIG = "/index.php?c=Admin/ShopAdmin/ShopConfig/shopconfig&a=getShopConfig"
MENUS = "/index.php?c=Admin/GetMenusConfig"
SITE_CONFIG = "/index.php?c=admin/config/siteconfig&a=getinfo"
LEVELS = "/index.php?c=admin/ShopAdmin/user/userconfig&a=getlevellist"
REG_ITEMS = "/index.php?c=admin/ShopAdmin/user/userconfig&a=getregitemlist"
GALLERY = "/index.php?c=admin/gallery/sitegallerymanage&a=getlist"
DOWNLOADS = "/index.php?c=admin/download/download&a=GetList"
ADMINS = "/index.php?c=admin/ShopAdmin/Account/Adminmanage&a=GetList"
FORM_DEFS = "/index.php?c=admin/form/formmanage&a=getlist"
FORM_ROWS = "/index.php?c=admin/form/formmanage&a=getrows"
SITE_PAGES_DIR = OUT / "site_pages"

# 把全部 143 个模块的 383 个端点扫过一遍后，这些是确实有数据、且对重建新站有用的
FINANCE_LIST = "/index.php?c=Admin/ShopAdmin/Finance/Financemanage&a=GetList"
SEO_LIST = "/index.php?c=admin/ShopAdmin/Seo/SeoManage&a=getList"
FRIENDLY_URL = "/index.php?c=Admin/SiteAdmin/Config/FriendlyUrl&a=getInfo"
SITE_REWRITE = "/index.php?c=admin/config/siterewrite&a=getlist"
LOGISTICS = "/index.php?c=admin/ShopAdmin/business/logisticsmanage&a=getlist"

# 配置类：统一存进一个 site_settings.json（都是小对象，散成十几个文件反而难找）
SETTING_ENDPOINTS = {
    "shop_config_full": "/index.php?c=admin/ShopAdmin/ShopConfig/Shopconfig&a=GetInfo",
    "payment_config": "/index.php?c=Admin/ShopAdmin/ShopConfig/shopconfig&a=getpaymentconfig",
    "login_config": "/index.php?c=Admin/ShopAdmin/User/Userconfig&a=getLoginConfig",
    "sms_config": "/index.php?c=admin/business/shopconfig&a=GetSmsConfig",
    "site_ssl": "/index.php?c=admin/config/SiteSsl&a=getInfo",
    "watermark": "/index.php?c=admin/config/WaterMark&a=getInfo",
    "site_langs": "/index.php?c=admin/config/sitelangs&a=GetList",
    "domains": "/index.php?c=admin/ShopAdmin/Seo/SeoSetting&a=getDomainList",
    "pickup_config": "/index.php?c=Admin/ShopAdmin/ShopConfig/shopconfig&a=getShopPickupConfig",
    "freight_relief": "/index.php?c=Admin/ShopAdmin/ShopConfig/shopconfig&a=getRelieveFreightConfig",
    "finance_setting": "/index.php?c=Admin/ShopAdmin/Finance/Financemanage&a=getSetting",
}


def fetch_categories(mall, force=False):
    j = mall.cached("categories", lambda: mall.json(CATEGORY_LIST, {"page": 1}), force=force)
    return j.get("list") or []


def fetch_products(mall, force=False):
    listing = mall.paginate("product_list", PRODUCT_LIST, page_param="curPage",
                            extra={"pagesize": 20}, force=force)
    out = []
    for i, row in enumerate(listing, 1):
        pid = row.get("ProductID")
        try:
            j = mall.cached(f"product_{pid}",
                            lambda pid=pid: mall.json(PRODUCT_INFO.format(id=pid), method="get"),
                            force=force)
            info = j.get("info") or {}
        except Exception as e:
            mall.failures.append({"kind": "product_detail", "id": pid, "error": str(e)})
            continue
        merged = dict(row)
        merged.update({k: v for k, v in info.items() if k not in ("freightTemp",)})
        merged["class_ids"] = info.get("ClassID") or []
        out.append(merged)
        if i % 20 == 0 or i == len(listing):
            print(f"    商品详情 {i}/{len(listing)}")
    return out


def fetch_site_page_urls(session):
    """从 sitemap 取站点页面清单（前台公开页，不需要登录态）。"""
    r = session.get(SITE_HOST + "/sitemap.xml", timeout=60)
    out = []
    for u in re.findall(r"<loc>(.*?)</loc>", r.text):
        kind = classify_site_url(u)
        if kind:
            out.append((kind, u))
    return out


def fetch_site_extras(mall, force=False):
    """站点/公司信息、会员等级、注册项、图片库、下载、管理员。

    这些量都很小，但缺了就得回头再抓一次，一并存下。
    """
    def one(key, path, **kw):
        return mall.cached(key, lambda: mall.json(path, method=kw.pop("method", "post")),
                           force=force)

    # 凭据可能藏在嵌套的 UserInfo 里，递归剔除
    site_config = strip_secrets(one("site_config", SITE_CONFIG).get("info") or {})

    extras = {
        "site_config": site_config,
        "member_levels": one("member_levels", LEVELS).get("list") or [],
        "member_reg_items": one("member_reg_items", REG_ITEMS).get("list") or [],
        "site_gallery": one("site_gallery", GALLERY).get("list") or [],
        "downloads": one("downloads", DOWNLOADS).get("list") or [],
        "admins": one("admins", ADMINS).get("list") or [],
    }

    # 自定义表单：先取表单定义，再逐表取提交记录
    forms = one("form_defs", FORM_DEFS).get("list") or []
    rows = []
    for f in forms:
        if str(f.get("recordcount") or "0") in ("0", "", "None"):
            continue
        fid = f.get("ID")
        d = mall.cached(f"form_rows_{fid}",
                        lambda fid=fid: mall.json(FORM_ROWS, {"id": fid, "keyword": "",
                                                              "page": 1, "pagesize": 500,
                                                              "status": "1"}),
                        force=force)
        for r in d.get("list") or []:
            if is_blank_form_row(r):
                continue
            r["_form_name"] = f.get("Name") or ""
            rows.append(r)
    extras["form_defs"] = forms
    extras["form_rows"] = rows
    return extras


def fetch_deep_extras(mall, force=False):
    """财务流水、SEO、URL 重写、物流公司、各类站点配置。

    这几项是把全部 143 个模块的 383 个端点扫过一遍后才找到的 —— 单看菜单或
    常规命名都发现不了（例如财务流水挂在 `Finance/Financemanage&a=GetList`，
    而不是 `financelist`）。
    """
    def one(key, path, data=None):
        return mall.cached(key, lambda: mall.json(path, data or {}), force=force)

    settings = {}
    for name, path in SETTING_ENDPOINTS.items():
        settings[name] = strip_secrets(unwrap_setting(one(f"setting_{name}", path)))

    return {
        # 674 条收款台账；接口接受 pagesize=1000，一次取全
        "finance_records": mall.paginate("finance_list", FINANCE_LIST,
                                         extra={"pagesize": 1000}, force=force),
        "seo_settings": one("seo_list", SEO_LIST).get("list") or [],
        # 默认每页 20，用 page/pageSize 取全（注意是驼峰 pageSize）
        "friendly_urls": one("friendly_url", FRIENDLY_URL,
                             {"page": 1, "pageSize": 500}).get("data") or [],
        "site_rewrite": one("site_rewrite", SITE_REWRITE).get("list") or [],
        "logistics_companies": one("logistics", LOGISTICS).get("list") or [],
        "site_settings": settings,
    }


def fetch_articles(mall, force=False):
    listing = mall.paginate("news_list", NEWS_LIST, force=force)
    out = []
    for i, row in enumerate(listing, 1):
        aid = row.get("ArticleID")
        try:
            j = mall.cached(f"news_{aid}",
                            lambda aid=aid: mall.json(NEWS_INFO.format(id=aid), method="get"),
                            force=force)
            info = j.get("info") or {}
        except Exception as e:
            mall.failures.append({"kind": "article_detail", "id": aid, "error": str(e)})
            info = {}
        merged = dict(row)
        merged.update(info)
        out.append(merged)
        if i % 10 == 0 or i == len(listing):
            print(f"    文章详情 {i}/{len(listing)}")
    return out


def fetch_all(mall, force=False):
    print("  分类 …")
    categories = fetch_categories(mall, force)
    print(f"  → {len(categories)} 条")

    print("  商品 …")
    products = fetch_products(mall, force)

    print("  会员 …")
    members = mall.paginate("member_list", MEMBER_LIST, force=force)

    print("  订单 …")
    orders = mall.paginate("order_list", ORDER_LIST, force=force, method="get")

    print("  文章 …")
    articles = fetch_articles(mall, force)
    article_classes = (mall.cached("news_class", lambda: mall.json(NEWS_CLASS, {"page": 1}))
                       .get("list") or [])

    print("  优惠券 / 运费模板 / 店铺配置 / 站点信息 …")
    coupons = mall.paginate("coupon_list", COUPON_LIST, force=force)
    freight = mall.cached("freight", lambda: mall.json(FREIGHT)).get("info") or {}
    shop_config = mall.cached("shopconfig", lambda: mall.json(SHOPCONFIG)).get("info") or {}
    menus = mall.cached("menus", lambda: mall.json(MENUS, method="get"))
    site = {
        "userinfo": strip_secrets(menus.get("userinfo")),
        "siteinfo": menus.get("siteinfo"),
    }

    print("  站点配置 / 会员等级 / 表单记录 …")
    extras = fetch_site_extras(mall, force)

    print("  财务流水 / SEO / URL 重写 / 物流 …")
    deep = fetch_deep_extras(mall, force)

    data = {
        "categories": categories,
        "products": products,
        "members": members,
        "orders": orders,
        "articles": articles,
        "article_classes": article_classes,
        "coupons": coupons,
        "freight_templates": freight,
        "shop_config": shop_config,
        "site": site,
        "extras": extras,
        "deep": deep,
    }

    # 抓完自检：主键必须唯一，否则说明某个接口的分页没真的翻页
    for table, key in (("categories", "ClassID"), ("products", "ProductID"),
                       ("members", "UserID"), ("orders", "OrderID"),
                       ("articles", "ArticleID")):
        dups = duplicate_ids(data[table], key)
        if dups:
            print(f"  ⚠️ {table} 有 {len(dups)} 个重复 {key}，分页可能没生效")
            mall.failures.append({"kind": "duplicate_ids", "table": table,
                                  "key": key, "dup_count": len(dups),
                                  "sample": dups[:5]})

    return data


# --------------------------------------------------------------------------
# 图片与详情
# --------------------------------------------------------------------------

PRODUCT_IMG_FIELDS = ("BigImages", "SmallImages", "ImgBig", "ImgSmall",
                      "ImgBig1", "ImgSmall1", "ImgBig2", "ImgSmall2",
                      "ImgBig3", "ImgSmall3", "ImgBig4", "ImgSmall4")


def download_product_images(mall, products):
    """商品主图（含订单明细里引用到的）。返回 {标识: 落盘相对路径}。"""
    idents = set()
    for p in products:
        for f in PRODUCT_IMG_FIELDS:
            idents.update(split_image_field(p.get(f)))
    for o in mall.orders:
        for g in o.get("Goods") or []:
            idents.update(split_image_field(g.get("Image")))

    print(f"  商品图片 {len(idents)} 个 …")
    mapping, missing = {}, []
    for i, ident in enumerate(sorted(idents), 1):
        rel = resolve_and_download(mall, ident, kind="product")
        if rel:
            mapping[ident] = rel
        else:
            missing.append(ident)
        if i % 50 == 0 or i == len(idents):
            print(f"    {i}/{len(idents)}")
    if missing:
        mall.failures.append({"kind": "product_images", "count": len(missing),
                              "idents": missing[:50]})
    return mapping


def write_detail_html(mall, products, img_map):
    """富文本详情落盘，<img src> 改写为指向本地 images/。"""
    DETAILS.mkdir(parents=True, exist_ok=True)
    written, unresolved = 0, []
    for p in products:
        pid = p.get("ProductID")
        blocks = p.get("Content")
        if not blocks:
            continue
        if isinstance(blocks, str):
            try:
                blocks = json.loads(blocks)
            except ValueError:
                unresolved.append(pid)
                continue
        html = "\n".join(b.get("content", "") for b in blocks if isinstance(b, dict))
        if not html.strip():
            continue
        mapping = {}
        for src in extract_img_srcs(html):
            if src in img_map:
                mapping[src] = detail_rel_path(img_map[src])
                continue
            rel = resolve_and_download(mall, src)
            if rel:
                img_map[src] = rel
                mapping[src] = detail_rel_path(rel)
            else:
                unresolved.append(f"{pid}:{src}")
        body = replace_img_srcs(html, mapping)
        page = ("<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                f"<title>{p.get('Name', '')}</title></head><body>\n{body}\n</body></html>\n")
        (DETAILS / f"{pid}.html").write_text(page, encoding="utf-8")
        written += 1
    if unresolved:
        mall.failures.append({"kind": "detail_images", "count": len(unresolved),
                              "samples": unresolved[:30]})
    return written


def download_article_images(mall, articles):
    idents = set()
    for a in articles:
        for f in ("ImgSmall", "ImgBig", "ShareImg", "AuthorImg"):
            idents.update(split_image_field(a.get(f)))
    mapping, missing = {}, []
    print(f"  文章图片 {len(idents)} 个 …")
    for ident in sorted(idents):
        rel = resolve_and_download(mall, ident, kind="article")
        if rel:
            mapping[ident] = rel
        else:
            missing.append(ident)
    if missing:
        mall.failures.append({"kind": "article_images", "count": len(missing),
                              "idents": missing[:50]})
    return mapping


def scrape_site_pages(mall, session, img_map):
    """把站点单页/落地页整页存下来，图片本地化。

    页面是「模块拼装」的（正文散在多个 module 容器里），按模块抽正文不可靠，
    所以整页保存、图片改指本地 —— 离线可看，内容也不丢。
    """
    SITE_PAGES_DIR.mkdir(parents=True, exist_ok=True)
    (SITE_PAGES_DIR / "text").mkdir(parents=True, exist_ok=True)
    urls = fetch_site_page_urls(session)
    print(f"  站点页面 {len(urls)} 个 …")
    index, failed = [], []
    for i, (kind, url) in enumerate(urls, 1):
        time.sleep(REQUEST_GAP)
        try:
            r = session.get(url, timeout=60)
        except Exception as e:
            failed.append({"url": url, "error": str(e)})
            continue
        raw = r.text
        if r.status_code != 200 or not raw.strip():
            failed.append({"url": url, "error": f"HTTP {r.status_code}"})
            continue
        mapping, srcs = {}, [s for s in extract_img_srcs(raw) if is_localizable_img(s)]
        for s in srcs:
            rel = img_map.get(s) or resolve_and_download(mall, s)
            if not rel:
                continue
            img_map[s] = rel
            mapping[s] = detail_rel_path(rel)
        slug = page_slug(kind, url)
        (SITE_PAGES_DIR / f"{slug}.html").write_text(replace_img_srcs(raw, mapping),
                                                     encoding="utf-8")
        text = html_to_text(raw)
        (SITE_PAGES_DIR / "text" / f"{slug}.txt").write_text(text, encoding="utf-8")
        m = re.search(r"<title>(.*?)</title>", raw, re.S | re.I)
        index.append({"slug": slug, "kind": kind, "url": url,
                      "title": html_mod.unescape(m.group(1)).strip() if m else "",
                      "images": len(srcs), "localized": len(mapping),
                      "text_chars": len(text)})
        if i % 10 == 0 or i == len(urls):
            print(f"    {i}/{len(urls)}")
    write_json(SITE_PAGES_DIR / "index.json", index)
    if failed:
        mall.failures.append({"kind": "site_pages", "count": len(failed), "samples": failed[:10]})
    return index


# --------------------------------------------------------------------------
# 落盘
# --------------------------------------------------------------------------

def sanitize_raw_cache():
    """把 raw 缓存里的凭据就地清干净 —— **每一个** raw 文件都要过一遍。

    缓存是为断点续抓用的，但 `oldMallData/` 整个要跟着数据走（拷机器、备份），
    让微信支付密钥、支付宝私钥、短信口令、地图 API Key 躺在那儿没有道理。
    剔掉不影响续抓（这些字段抓取时本来就不用）。

    先前只清理了 menus / site_config 两份，结果新加的 setting_* 缓存又漏了
    （AlipayLoginKey / QQAppKey / SMSPassword / Kuaidi100Key）—— 所以改成全量遍历。
    会员 `Password` 在 KEEP_KEYS 里，不受影响。
    """
    for f in sorted(RAW.glob("*.json")):
        try:
            d = json.loads(f.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            continue
        f.write_text(json.dumps(strip_secrets(d), ensure_ascii=False), encoding="utf-8")


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")


def write_readme(counts):
    (OUT / "README.md").write_text(f"""# 旧商城数据快照（oldMallData）

**来源**：`u247521.yz168.cc`（域名 `yuwangchenfa.com`，SiteID `{SITE_ID}`）
**方式**：管理端 API 直取（不是手工导出 Excel），抓取脚本 `scripts/scrape_old_mall.py`
**用途**：之后部署到正式环境时导入新库 —— 所有 JSON 都保留**旧系统主键**
（`ProductID` / `ClassID` / `UserID` / `OrderID`）作为关联键。

> ⚠️ 本目录含真实客户个人信息（手机号、收货地址、联系人）与会员密码哈希，
> 已加入 `.gitignore`，**禁止入库**。

## 条数

| 数据 | 文件 | 条数 |
|---|---|---|
| 分类 | `data/categories.json` | {counts['categories']} |
| 商品 | `data/products.json` | {counts['products']} |
| 会员 | `data/members.json` | {counts['members']} |
| 订单 | `data/orders.json` | {counts['orders']} |
| 订单明细 | `data/order_items.json`（由订单内嵌 `Goods` 拍平） | {counts['order_items']} |
| 文章 | `data/articles.json` | {counts['articles']} |
| 文章分类 | `data/article_classes.json` | {counts['article_classes']} |
| 优惠券 | `data/coupons.json` | {counts['coupons']} |
| 商品详情页 | `product_details/<ProductID>.html` | {counts['detail_pages']} |
| 站点单页/落地页 | `site_pages/*.html`（附 `site_pages/text/*.txt` 纯文本） | {counts['site_pages']} |
| 表单提交 | `data/form_rows.json`（发票抬头、定制需求） | {counts['form_rows']} |
| 会员等级 | `data/member_levels.json` | {counts['member_levels']} |
| 财务流水 | `data/finance_records.json`（收款台账） | {counts['finance_records']} |
| 商品 SEO | `data/seo_settings.json` | {counts['seo_settings']} |
| URL 重写规则 | `data/friendly_urls.json` + `site_rewrite.json` | {counts['friendly_urls']} |
| 物流公司 | `data/logistics_companies.json` | {counts['logistics_companies']} |
| 站点配置合集 | `data/site_settings.json`（店铺/支付/登录/短信/SSL/水印/语言/域名） | — |
| 图片 | `images/` | {counts['images']} |

## 站点页面（`site_pages/`）

前台公开页，**整页保存**、图片已本地化，离线可看；`site_pages/text/` 下是同名纯文本，便于检索。
清单见 `site_pages/index.json`（slug / 原始 URL / 标题 / 字数 / 图片数）。
内容页是多模块拼装的、没有单一「正文容器」，所以整页保留 —— 重建新站时按页面搬运。

## 目录

```
data/                       各表 JSON（原始接口字段 + 保留旧主键）
data/order_items.json       订单明细，带 OrderID / ProductID 外键
product_details/<id>.html   富文本详情，<img src> 已改写为指向 ../images/
site_pages/                 站点单页与落地页（整页 + 纯文本 + index.json）
images/product/             商品主图（_b 大图 / _s 小图）
images/content/<YYYYMM>/    正文内嵌图（保留月份子目录防重名）
images/article/             文章配图
```

## 字段映射（→ 新库）

- 分类 → `product_category`：`Name`→`name`，`ParentID`→`parent_id`（`0` 为一级），
  `ShowOrder`→`sort`。旧库最多两层，正好对上 yudao 的两层硬限制。
- 商品 → `product_spu` / `product_sku`：`Name`→`name`，`Price`→`price`（元→分**用 Decimal**，
  16/282 条用 float×100 会错 1 分），`ProductQuantity`→`stock`，`SalesCount`/`Hits` 需建完后回填。
  `class_ids` 要挂到**二级**分类。`ProDesc`→`introduction`，`Content`→`description`（见 `product_details/`）。
- 会员 → `member_user`：`Mobile`→`mobile`，`NickName`→`nickname`，`CrTime`→`create_time`。
- 订单 → `trade_order`(+`trade_order_item`)：`Contact`/`Mobile`/`Address`/`ProvinceID`/`CityID`/`DistrictID`
  是**本次新补齐**的收货信息；`PayTime`/`DeliveryTime`/`LogisticsName` 同样新补齐。

## 已知缺口

- 会员 `Password` 是**旧系统的 MD5 哈希**，新系统用不到（C 端为手机号+短信验证码登录），
  保留仅为数据完整。
- `data/site.json` 与 `data/site_config.json` **已剔除全部凭据**（微信支付密钥、支付宝
  公私钥、短信口令、地图 API Key 等）：对导入无价值，且是活凭据。`--force` 抓取后也会
  就地清理 `.cache/raw/` 里带凭据的两份缓存，所以本目录可以整包拷贝而不会外泄密钥。
""", encoding="utf-8")


def main():
    from oldmall_probe import load_cookies, load_env, new_session

    only_images = "--images" in sys.argv
    force = "--force" in sys.argv

    env = load_env()
    session, base, admin_url = new_session(env)
    if not load_cookies(session):
        print("没有会话缓存。先跑：python scripts/oldmall_probe.py  （抓验证码）")
        return 1
    mall = OldMall(session, base, admin_url)

    try:
        if only_images:
            products = json.loads((DATA / "products.json").read_text(encoding="utf-8"))
            mall.orders = json.loads((DATA / "orders.json").read_text(encoding="utf-8"))
            img_map = download_product_images(mall, products)
            print(f"  完成：{len(img_map)} 张")
            return 0

        data = fetch_all(mall, force=force)
        mall.orders = data["orders"]
        sanitize_raw_cache()

        # 分类路径（导入时用得上）与订单明细拍平
        for c in data["categories"]:
            c["path"] = resolve_category_path(c.get("ClassID"), data["categories"])
        order_items = []
        for o in data["orders"]:
            order_items.extend(split_order_goods(o))

        write_json(DATA / "categories.json", data["categories"])
        write_json(DATA / "products.json", data["products"])
        write_json(DATA / "members.json", data["members"])
        write_json(DATA / "orders.json", data["orders"])
        write_json(DATA / "order_items.json", order_items)
        write_json(DATA / "articles.json", data["articles"])
        write_json(DATA / "article_classes.json", data["article_classes"])
        write_json(DATA / "coupons.json", data["coupons"])
        write_json(DATA / "freight_templates.json", data["freight_templates"])
        write_json(DATA / "shop_config.json", data["shop_config"])
        write_json(DATA / "site.json", data["site"])
        ex = data["extras"]
        for key in ("site_config", "member_levels", "member_reg_items",
                    "site_gallery", "downloads", "admins", "form_defs", "form_rows"):
            write_json(DATA / f"{key}.json", ex[key])
        for key in ("finance_records", "seo_settings", "friendly_urls",
                    "site_rewrite", "logistics_companies", "site_settings"):
            write_json(DATA / f"{key}.json", data["deep"][key])

        print("  图片 …")
        img_map = download_product_images(mall, data["products"])
        art_map = download_article_images(mall, data["articles"])
        img_map.update(art_map)
        detail_pages = write_detail_html(mall, data["products"], img_map)
        site_pages = scrape_site_pages(mall, session, img_map)

        images = [p for p in IMAGES.rglob("*") if p.is_file()]
        empty = [str(p) for p in images if p.stat().st_size == 0]
        if empty:
            mall.failures.append({"kind": "zero_byte_images", "paths": empty[:50]})

        counts = {
            "categories": len(data["categories"]),
            "products": len(data["products"]),
            "members": len(data["members"]),
            "orders": len(data["orders"]),
            "order_items": len(order_items),
            "articles": len(data["articles"]),
            "article_classes": len(data["article_classes"]),
            "coupons": len(data["coupons"]),
            "detail_pages": detail_pages,
            "site_pages": len(site_pages),
            "form_rows": len(ex["form_rows"]),
            "member_levels": len(ex["member_levels"]),
            "finance_records": len(data["deep"]["finance_records"]),
            "seo_settings": len(data["deep"]["seo_settings"]),
            "friendly_urls": len(data["deep"]["friendly_urls"]),
            "logistics_companies": len(data["deep"]["logistics_companies"]),
            "images": len(images),
        }
        manifest = {
            "source": {"admin": admin_url, "site_id": SITE_ID, "cdn": CDN},
            "scraped_at": time.strftime("%Y-%m-%d %H:%M:%S"),
            "counts": counts,
            "image_map_entries": len(img_map),
            "failures": mall.failures,
        }
        write_json(OUT / "manifest.json", manifest)
        write_readme(counts)

        print("\n完成：")
        for k, v in counts.items():
            print(f"  {k}: {v}")
        print(f"  失败项: {len(mall.failures)}")
        return 0

    except SessionExpired as e:
        print(f"\n⚠️ 会话失效：{e}")
        print("重新登录：python scripts/oldmall_probe.py  →  python scripts/oldmall_probe.py <验证码>")
        return 2


if __name__ == "__main__":
    sys.exit(main())
