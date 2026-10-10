package cn.iocoder.yudao.module.promotion.convert.banner;

import cn.iocoder.yudao.module.promotion.controller.app.banner.vo.AppBannerRespVO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.banner.BannerDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * C 端横幅返回体的字段契约。
 *
 * 为什么要专门测：**MapStruct 对「目标有、源没有」的字段只报 WARN，不会编译失败** ——
 * 于是"后台能填、前台收不到"这种缺口可以一路静默到线上。
 *
 * 具体盯的是 `memo`（后台表单里的「描述」）：商城页横幅是**组合式**的，
 * 文案由前端排版，其中副标题与胶囊就来自 `memo` 的两行。少了它，横幅只剩主标题。
 */
class BannerConvertTest {

    @Test
    void testConvertList01_mapsMemo() {
        BannerDO banner = new BannerDO();
        banner.setId(1L);
        banner.setTitle("从设计到打印\n每一步，都有好搭档");
        banner.setPicUrl("http://x/product.webp");
        banner.setUrl("http://x");
        banner.setMemo("标签软件 · 打印设备 · 标签耗材\nMaxLabel 软件开发中");

        AppBannerRespVO vo = BannerConvert.INSTANCE.convertList01(List.of(banner)).get(0);

        assertEquals(banner.getMemo(), vo.getMemo(), "「描述」必须透到 C 端，否则横幅的副标题与胶囊没有来源");
        assertEquals(banner.getTitle(), vo.getTitle());
        assertEquals(banner.getPicUrl(), vo.getPicUrl());
        assertEquals(banner.getUrl(), vo.getUrl());
    }

}
