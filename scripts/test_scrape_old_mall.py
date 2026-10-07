"""旧商城抓取的**纯函数**测试（标准库 unittest，无第三方依赖）。

只测「与网络无关」的解析与改写逻辑：图片 URL 还原、正文图片改写、分类路径还原、
订单明细拍平、凭据剔除。不测真实请求（那部分靠跑完后的 manifest 对账）。

跑法：python scripts/test_scrape_old_mall.py

⚠️ 用例里的文件名与 ClassID 不是编的，是从真实接口响应里摘出来的（每条都注了出处），
   它们同时是抓取结果的验收基准。对照 `oldMallData/data/` 下的同名数据可以复核。
"""
import unittest

from scrape_old_mall import (
    CDN,
    SITE_HOST,
    SITE_ID,
    classify_site_url,
    detail_rel_path,
    html_to_text,
    is_blank_form_row,
    is_localizable_img,
    page_slug,
    duplicate_ids,
    extract_img_srcs,
    img_candidates,
    local_img_path,
    replace_img_srcs,
    repair_image_name,
    resolve_category_path,
    split_image_field,
    split_order_goods,
    strip_secrets,
    unwrap_setting,
)

# 出处：GetProductInfo&id=12284615 的 BigImages/SmallImages
PRODUCT_IMG = "20260908194944348A3639D9206ED1_s.png"
# 出处：orderAdministrationList 首条 Goods[0].Image（已是完整路径）
GOODS_IMG = "/comdata/88043/product/202609081937347813C0DBCDE8EF83_s.png"
# 出处：GetProductInfo&id=12284615 的 Content 正文内嵌图（实测 200）
CONTENT_IMG = "/comdata/88043/202508/20250829155143ec614d.jpg"


class TestImgCandidates(unittest.TestCase):
    """字段里的图片标识 → 候选绝对 URL。"""

    def test_bare_product_filename_tries_product_prefix_first(self):
        cands = img_candidates(PRODUCT_IMG)
        self.assertEqual(
            cands[0],
            f"{CDN}/comdata/{SITE_ID}/product/{PRODUCT_IMG}",
        )

    def test_bare_filename_also_offers_month_and_news_fallbacks(self):
        cands = img_candidates(PRODUCT_IMG)
        # 文件名前 6 位是 YYYYMM，可作为第二候选
        self.assertIn(f"{CDN}/comdata/{SITE_ID}/202609/{PRODUCT_IMG}", cands)
        self.assertIn(f"{CDN}/comdata/{SITE_ID}/news/{PRODUCT_IMG}", cands)

    def test_hint_prefix_wins(self):
        cands = img_candidates("abc.png", hint="icontemp/202410")
        self.assertEqual(cands[0], f"{CDN}/comdata/{SITE_ID}/icontemp/202410/abc.png")

    def test_full_path_gets_cdn_host(self):
        self.assertEqual(img_candidates(GOODS_IMG), [CDN + GOODS_IMG])

    def test_protocol_relative_becomes_https(self):
        self.assertEqual(
            img_candidates("//img.wds168.cn/comdata/88043/product/a.png"),
            ["https://img.wds168.cn/comdata/88043/product/a.png"],
        )

    def test_absolute_url_passes_through(self):
        u = "https://yuwangchenfa.com/comdata/88043/product/a.png"
        self.assertEqual(img_candidates(u), [u])

    def test_blank_inputs_yield_nothing(self):
        for blank in (None, "", "   "):
            self.assertEqual(img_candidates(blank), [])

    def test_candidates_are_deduplicated_and_ordered(self):
        cands = img_candidates("20260908194944348A3639D9206ED1_s.png")
        self.assertEqual(len(cands), len(set(cands)))


class TestUnwrapSetting(unittest.TestCase):
    """配置类接口的载荷位置不统一：info / data / list / 顶层都见过。"""

    def test_info_is_preferred(self):
        self.assertEqual(unwrap_setting({"success": True, "info": {"a": 1}}), {"a": 1})

    def test_data_is_used_when_no_info(self):
        self.assertEqual(unwrap_setting({"success": True, "data": {"b": 2}}), {"b": 2})

    def test_list_is_used_when_no_info_or_data(self):
        self.assertEqual(unwrap_setting({"success": True, "list": [1, 2]}), [1, 2])

    def test_top_level_fallback_drops_framework_fields(self):
        resp = {"success": True, "msg": "ok", "cookies": "<script>setCookie(...)</script>",
                "RefundsPeriod": "7"}
        self.assertEqual(unwrap_setting(resp), {"RefundsPeriod": "7"})

    def test_csrf_cookie_never_survives(self):
        """cookies 每次请求都换、还带 CSRF token，存进快照是纯噪音。"""
        out = unwrap_setting({"success": True, "cookies": "CsrfTokenP=abc", "x": 1})
        self.assertNotIn("cookies", out)

    def test_non_dict_passes_through(self):
        self.assertEqual(unwrap_setting([1, 2]), [1, 2])


class TestIsBlankFormRow(unittest.TestCase):
    """空占位表单记录。

    出处：表单 119630「有 86 条记录」，逐条查全是 {"x_empty": "nodata", ...}，
    没有任何 cc_ 实际字段 —— 不是真实提交。
    """

    BLANK = {"x_empty": "nodata", "ID": "4779611", "FormID": "119630",
             "CrTime": "2024-10-16 14:04:14", "Status": "0"}

    def test_nodata_placeholder_is_blank(self):
        self.assertTrue(is_blank_form_row(self.BLANK))

    def test_blank_is_detected_without_the_marker(self):
        self.assertTrue(is_blank_form_row({"ID": "1", "cc_附加要求:": ""}))

    def test_real_submission_with_fields_is_kept(self):
        row = {"ID": "5168988", "cc_名称": "义乌市综宏科技有限公司",
               "cc_税号": "91330782MA2E8E3Y2X"}
        self.assertFalse(is_blank_form_row(row))

    def test_single_filled_field_is_enough(self):
        self.assertFalse(is_blank_form_row({"ID": "1", "cc_选择材质：": "高级铜版纸",
                                            "cc_附加要求:": ""}))

    def test_none_like_values_do_not_count(self):
        self.assertTrue(is_blank_form_row({"cc_a": "None", "cc_b": "null", "cc_c": " "}))

    def test_non_dict_is_blank(self):
        self.assertTrue(is_blank_form_row(None))


class TestClassifySiteUrl(unittest.TestCase):
    """站点页面分类。清单来自前台 sitemap.xml（实测 491 个 URL）。"""

    def test_content_pages(self):
        self.assertEqual(classify_site_url(f"{SITE_HOST}/Content/2878529.html"), "content")

    def test_named_landing_pages(self):
        for p in ("/BuGanJiaoDingZhi", "/QiYeYingYong", "/ZhuiSuXiTong", "/ChanPinBiaoShiDaYinFangAn"):
            self.assertEqual(classify_site_url(SITE_HOST + p), "landing", p)

    def test_download_pages(self):
        self.assertEqual(classify_site_url(f"{SITE_HOST}/DownLoad/184393.html"), "download")

    def test_mall_urls_are_not_site_pages(self):
        # 商品/文章/分类另有抓取路径，不该重复进 site_pages
        for p in ("/ProductDetail/12284615.html", "/NewsDetail/5356131.html",
                  "/Product/618874.html", "/NewsList/2.html", "/ProductIndex"):
            self.assertIsNone(classify_site_url(SITE_HOST + p), p)

    def test_homepage_is_not_a_site_page(self):
        self.assertIsNone(classify_site_url(SITE_HOST + "/"))
        self.assertIsNone(classify_site_url(SITE_HOST))

    def test_blank_is_safe(self):
        for blank in (None, ""):
            self.assertIsNone(classify_site_url(blank))


class TestPageSlug(unittest.TestCase):

    def test_content_page_uses_its_id(self):
        self.assertEqual(page_slug("content", f"{SITE_HOST}/Content/2878529.html"), "content_2878529")

    def test_landing_page_is_slugified(self):
        self.assertEqual(page_slug("landing", f"{SITE_HOST}/BuGanJiaoDingZhi"), "landing_buganjiaodingzhi")

    def test_slugs_are_unique_across_kinds(self):
        a = page_slug("content", f"{SITE_HOST}/Content/123.html")
        b = page_slug("landing", f"{SITE_HOST}/Content/123")
        self.assertNotEqual(a, b)

    def test_nested_download_path(self):
        self.assertEqual(page_slug("download", f"{SITE_HOST}/DownLoad/184393.html"), "download_download_184393_html")


class TestIsLocalizableImg(unittest.TestCase):
    """只本地化站点素材；static.wds168.cn 的 CSS/图标保持外链。"""

    def test_site_assets_are_localizable(self):
        for s in ("/comdata/88043/202508/a.jpg",
                  "//img.wds168.cn/comdata/88043/product/a_s.png",
                  f"{SITE_HOST}/comdata/88043/202411/b.png"):
            self.assertTrue(is_localizable_img(s), s)

    def test_static_assets_are_left_alone(self):
        for s in ("//static.wds168.cn/scripts/iconfont/iconfont.css",
                  "//static.wds168.cn/scripts/slick/a.png",
                  "/admin/plugins/businesspackage/html/skinsrc/images/image-empty.png"):
            self.assertFalse(is_localizable_img(s), s)

    def test_non_string_is_safe(self):
        self.assertFalse(is_localizable_img(None))


class TestHtmlToText(unittest.TestCase):

    def test_tags_are_stripped(self):
        self.assertEqual(html_to_text("<p>应用方案</p><div>正文</div>"), "应用方案 正文")

    def test_script_and_style_are_dropped(self):
        html = "<style>a{color:red}</style><p>保留</p><script>var x=1</script>"
        self.assertEqual(html_to_text(html), "保留")

    def test_entities_are_unescaped(self):
        self.assertEqual(html_to_text("<p>A&amp;B&nbsp;C</p>"), "A&B C")

    def test_blank_is_safe(self):
        self.assertEqual(html_to_text(None), "")
        self.assertEqual(html_to_text(""), "")


class TestDetailRelPath(unittest.TestCase):
    """详情页引用图片的相对路径。

    详情页在 product_details/<id>.html，图片在 images/…，只差一层。
    写成两层（../../）会全部指空 —— 实测 392 个引用无一命中。
    """

    def test_one_level_up(self):
        self.assertEqual(
            detail_rel_path("images/content/202508/a.jpg"),
            "../images/content/202508/a.jpg",
        )

    def test_product_bucket(self):
        self.assertEqual(detail_rel_path("images/product/a_s.png"), "../images/product/a_s.png")

    def test_result_lands_on_the_real_file(self):
        from pathlib import Path
        page = Path(__file__).resolve().parents[1] / "oldMallData" / "product_details"
        rel = detail_rel_path("images/content/202508/20250829155143ec614d.jpg")
        self.assertTrue((page / rel).resolve().exists(),
                        "相对路径应解析到 oldMallData/images/ 下的真实文件")


class TestRepairImageName(unittest.TestCase):
    """源数据里偶有丢掉路径分隔符的值。

    出处：manifest 里 product_images 失败项的第二个
    "comdata88043product20250523151113039AA58F27F393D3_s.jpg"
    —— 补回斜杠后 CDN 上是 200（实测 21303 字节）。
    """

    def test_slashes_are_restored(self):
        self.assertEqual(
            repair_image_name("comdata88043product20250523151113039AA58F27F393D3_s.jpg"),
            "/comdata/88043/product/20250523151113039AA58F27F393D3_s.jpg",
        )

    def test_healthy_path_is_untouched(self):
        for ok in ("/comdata/88043/product/a.png", "a.png", "20260908194944348A3639D9206ED1_s.png"):
            self.assertEqual(repair_image_name(ok), ok)

    def test_repair_feeds_into_candidates(self):
        broken = "comdata88043product20250523151113039AA58F27F393D3_s.jpg"
        self.assertEqual(
            img_candidates(broken),
            [f"{CDN}/comdata/88043/product/20250523151113039AA58F27F393D3_s.jpg"],
        )

    def test_blank_is_safe(self):
        for blank in (None, ""):
            self.assertEqual(repair_image_name(blank), blank)


class TestSplitImageField(unittest.TestCase):
    """图片字段可能是逗号分隔的多张图。

    出处：products.json 里 BigImages 的实际值
    "20250507100226B123BBCB693D72BC_b.png,20250507100226EDB2636FCED3790C_b.png"
    —— 当成单个文件名去取会全部 404（实测 300~450 号之间连续失败）。
    """

    def test_single_filename(self):
        self.assertEqual(split_image_field("a_b.png"), ["a_b.png"])

    def test_comma_separated_pair(self):
        self.assertEqual(
            split_image_field("20250507100226B123BBCB693D72BC_b.png,20250507100226EDB2636FCED3790C_b.png"),
            ["20250507100226B123BBCB693D72BC_b.png", "20250507100226EDB2636FCED3790C_b.png"],
        )

    def test_surrounding_whitespace_is_trimmed(self):
        self.assertEqual(split_image_field(" a.png , b.png "), ["a.png", "b.png"])

    def test_empty_parts_are_dropped(self):
        self.assertEqual(split_image_field("a.png,,,b.png"), ["a.png", "b.png"])
        self.assertEqual(split_image_field("a.png,"), ["a.png"])

    def test_blank_and_none_yield_nothing(self):
        for blank in (None, "", "   ", ",", ",,,"):
            self.assertEqual(split_image_field(blank), [])

    def test_newline_separated_also_splits(self):
        self.assertEqual(split_image_field("a.png\nb.png"), ["a.png", "b.png"])


class TestLocalImgPath(unittest.TestCase):
    """图片落盘路径。product 与 content 分桶，content 保留月份子目录防重名。"""

    def test_product_image_by_kind(self):
        self.assertEqual(local_img_path(PRODUCT_IMG, "product"), f"images/product/{PRODUCT_IMG}")

    def test_content_image_keeps_month_subdir(self):
        self.assertEqual(
            local_img_path(CONTENT_IMG),
            "images/content/202508/20250829155143ec614d.jpg",
        )

    def test_full_product_path_is_detected_without_explicit_kind(self):
        self.assertEqual(
            local_img_path(GOODS_IMG),
            "images/product/202609081937347813C0DBCDE8EF83_s.png",
        )

    def test_query_string_is_dropped(self):
        self.assertEqual(
            local_img_path("/comdata/88043/202508/a.jpg?v=2"),
            "images/content/202508/a.jpg",
        )


class TestHtmlImageRewrite(unittest.TestCase):
    """正文 HTML 里的图片引用。"""

    HTML = (
        '<p style="text-align: center;">'
        f'<img src="{CONTENT_IMG}"/></p>'
        '<p><img alt="x" src="/comdata/88043/product/p1.png"></p>'
    )

    def test_extract_finds_every_src(self):
        self.assertEqual(
            extract_img_srcs(self.HTML),
            [CONTENT_IMG, "/comdata/88043/product/p1.png"],
        )

    def test_extract_handles_single_and_double_quotes(self):
        html = "<img src='/a.png'><img src=\"/b.png\">"
        self.assertEqual(extract_img_srcs(html), ["/a.png", "/b.png"])

    def test_extract_ignores_non_img_tags(self):
        self.assertEqual(extract_img_srcs('<a href="/x.png">t</a>'), [])

    def test_replace_rewrites_only_mapped_srcs(self):
        mapping = {
            CONTENT_IMG: "../../images/content/202508/20250829155143ec614d.jpg",
            "/comdata/88043/product/p1.png": "../../images/product/p1.png",
        }
        out = replace_img_srcs(self.HTML, mapping)
        self.assertIn('src="../../images/content/202508/20250829155143ec614d.jpg"', out)
        self.assertIn('src="../../images/product/p1.png"', out)
        self.assertNotIn(CONTENT_IMG, out)

    def test_replace_leaves_unmapped_src_untouched(self):
        out = replace_img_srcs('<img src="/unknown.png">', {})
        self.assertEqual(out, '<img src="/unknown.png">')

    def test_replace_preserves_other_attributes(self):
        out = replace_img_srcs('<p style="a"><img alt="x" src="/a.png"/></p>', {"/a.png": "l.png"})
        self.assertEqual(out, '<p style="a"><img alt="x" src="l.png"/></p>')

    def test_html_is_returned_unchanged_when_no_images(self):
        self.assertEqual(replace_img_srcs("<p>纯文字</p>", {}), "<p>纯文字</p>")


# 出处：getclasslist —— 618874(卷筒标签,一级) / 618880(经济热敏,挂在 618874 下)
CATEGORIES = [
    {"ClassID": "618874", "ParentID": "0", "Name": "卷筒标签"},
    {"ClassID": "618880", "ParentID": "618874", "Name": "经济热敏"},
    {"ClassID": "618876", "ParentID": "0", "Name": "打印机"},
]


class TestResolveCategoryPath(unittest.TestCase):

    def test_second_level_returns_both_names(self):
        self.assertEqual(
            resolve_category_path("618880", CATEGORIES),
            {"level1": "卷筒标签", "level2": "经济热敏"},
        )

    def test_first_level_has_no_second(self):
        self.assertEqual(
            resolve_category_path("618874", CATEGORIES),
            {"level1": "卷筒标签", "level2": None},
        )

    def test_unknown_id_is_not_guessed(self):
        self.assertEqual(
            resolve_category_path("999", CATEGORIES),
            {"level1": None, "level2": None},
        )

    def test_int_id_matches_str_key(self):
        self.assertEqual(
            resolve_category_path(618880, CATEGORIES),
            {"level1": "卷筒标签", "level2": "经济热敏"},
        )


# 出处：orderAdministrationList 首条（Money 300.00 = Price 30.00 × Amount 10）。
# Contact / Mobile 已换成占位值 —— 原值是真实客户的姓名与手机号，本仓库公开，
# 而这两个字段在下方断言里根本没用到（只验证 OrderID/ProductID/Price 的传递）。
ORDER = {
    "OrderID": "202609291643048054786",
    "Money": "300.00",
    "Contact": "张三",
    "Mobile": "13800138000",
    "Goods": [
        {
            "ItemID": "202609291643048054786_1",
            "OrderID": "202609291643048054786",
            "ProductID": "12284613",
            "ProductName": "可移胶热敏合成【横版】100*80*500张/卷",
            "Price": "30.00",
            "Amount": "10",
            "Image": GOODS_IMG,
        }
    ],
}


class TestSplitOrderGoods(unittest.TestCase):

    def test_rows_carry_the_order_foreign_key(self):
        rows = split_order_goods(ORDER)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["OrderID"], "202609291643048054786")
        self.assertEqual(rows[0]["ProductID"], "12284613")

    def test_price_and_amount_survive_as_strings(self):
        row = split_order_goods(ORDER)[0]
        self.assertEqual(row["Price"], "30.00")
        self.assertEqual(row["Amount"], "10")

    def test_row_totals_match_order_money(self):
        row = split_order_goods(ORDER)[0]
        self.assertEqual(float(row["Price"]) * int(row["Amount"]), float(ORDER["Money"]))

    def test_order_without_goods_yields_empty(self):
        self.assertEqual(split_order_goods({"OrderID": "1"}), [])
        self.assertEqual(split_order_goods({"OrderID": "1", "Goods": None}), [])


class TestStripSecrets(unittest.TestCase):
    """支付/邮件凭据不该跟数据一起落盘。"""

    def test_payment_and_mail_credentials_are_removed(self):
        userinfo = {
            "UserID": "1",
            "WxPayAppSecret": "s3cr3t",
            "WxPayKey": "k",
            "AlipayKey": "a",
            "SmtpPass": "p",
            "WxPayAppID": "wxfa951475f55eae66",
            "SiteName": "御旺宸发",
        }
        out = strip_secrets(userinfo)
        for k in ("WxPayAppSecret", "WxPayKey", "AlipayKey", "SmtpPass"):
            self.assertNotIn(k, out)
        # 非凭据字段要保留
        self.assertEqual(out["UserID"], "1")
        self.assertEqual(out["WxPayAppID"], "wxfa951475f55eae66")
        self.assertEqual(out["SiteName"], "御旺宸发")

    def test_none_is_tolerated(self):
        self.assertEqual(strip_secrets(None), {})

    def test_names_not_on_the_fixed_list_are_still_caught(self):
        """只列固定名字会漏 —— siteconfig 里这几个先前都没列到。"""
        from scrape_old_mall import is_secret_key
        for name in ("SMSPassword", "AlipayPrivateKey", "UnionPaySignPasswd",
                     "BaiduLbsKey", "APIPEMKeyPath", "SMSPass", "WxPayAppSecret",
                     "BmapKey", "QmapKey"):
            self.assertTrue(is_secret_key(name), name)

    def test_public_and_ordinary_fields_survive(self):
        from scrape_old_mall import is_secret_key
        for name in ("WxPayAppID", "WxPayMchID", "Company", "SiteName", "UserID"):
            self.assertFalse(is_secret_key(name), name)

    def test_alipay_public_key_is_also_dropped(self):
        """公钥本非机密，但对导入新库毫无用处 —— 一并剔掉，少一条特例。"""
        from scrape_old_mall import is_secret_key
        self.assertTrue(is_secret_key("AlipayPublicKey"))

    def test_seo_keywords_are_not_mistaken_for_keys(self):
        """这条是防「用 key 当片段」的回归：数据里真有这几个字段。"""
        from scrape_old_mall import is_secret_key
        for name in ("ArtKeyword", "ProKeyword", "SeoKeyword", "TagKeyword",
                     "EnableSmsFindPass"):
            self.assertFalse(is_secret_key(name), name)

    def test_member_password_hash_is_deliberately_kept(self):
        """会员 Password 是 MD5 哈希，所有者选的是「下载但排除版本库」，
        明确包含「密码哈希」。它也是快照里唯一剩下的凭据类字段。"""
        out = strip_secrets({"UserID": "1", "Password": "15a791e23f0708e1f6c34fc03a2666d3"})
        self.assertEqual(out["Password"], "15a791e23f0708e1f6c34fc03a2666d3")

    def test_kept_password_does_not_shield_other_passwords(self):
        out = strip_secrets({"Password": "keep", "SMSPassword": "drop", "SmtpPass": "drop"})
        self.assertEqual(list(out), ["Password"])

    def test_a_bare_list_is_not_wiped(self):
        """回归：早期版本对非 dict 一律返回 {}，把 site_langs / domains 这类
        直接给列表的配置静默清空了（len 变 0，看不出是 bug）。"""
        out = strip_secrets([{"a": 1, "SMSPassword": "x"}, {"a": 2}])
        self.assertEqual(out, [{"a": 1}, {"a": 2}])

    def test_lists_are_walked_too(self):
        """raw 缓存顶层常是 {"pages": [[页码, [行…]]]}，必须能走进去。"""
        resp = {"pages": [[1, [{"UserID": "1", "SMSPassword": "x", "Password": "keep"}]]]}
        out = strip_secrets(resp)
        self.assertEqual(out, {"pages": [[1, [{"UserID": "1", "Password": "keep"}]]]})

    def test_nested_credentials_are_removed(self):
        site = {
            "WebsiteInfo": {"Company": "北京御旺宸发科技有限公司"},
            "UserInfo": {"AlipayPrivateKey": "x", "SMSPassword": "y", "UserID": "1"},
        }
        out = strip_secrets(site)
        self.assertEqual(out["WebsiteInfo"]["Company"], "北京御旺宸发科技有限公司")
        self.assertEqual(out["UserInfo"], {"UserID": "1"})


class TestDuplicateIds(unittest.TestCase):
    """抓完的自检：分页参数写错时接口一直返回第 1 页，主键会整体重复。

    这不是假想 —— 商品列表用错了参数名（page 而非 curPage），15 页返回同一批
    20 个商品、凑出 300 行。行数看着对，只有查主键唯一性才发现得了。
    """

    def test_clean_rows_have_no_duplicates(self):
        rows = [{"ProductID": i} for i in range(1, 51)]
        self.assertEqual(duplicate_ids(rows, "ProductID"), [])

    def test_repeated_first_page_is_caught(self):
        page1 = [{"ProductID": i} for i in range(1, 21)]
        self.assertEqual(duplicate_ids(page1 * 15, "ProductID"),
                         [r["ProductID"] for r in page1] * 14)

    def test_missing_key_counts_as_a_duplicate(self):
        rows = [{"a": 1}, {"a": 1}]
        self.assertEqual(duplicate_ids(rows, "ProductID"), [None])

    def test_empty_and_none_are_safe(self):
        self.assertEqual(duplicate_ids([], "ProductID"), [])
        self.assertEqual(duplicate_ids(None, "ProductID"), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
