package cn.iocoder.yudao.module.promotion.controller.app.seckill;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.product.api.subscriptionplan.ProductSubscriptionPlanApi;
import cn.iocoder.yudao.module.promotion.controller.app.seckill.vo.activity.AppSeckillActivityPageReqVO;
import cn.iocoder.yudao.module.promotion.controller.app.seckill.vo.activity.AppSeckillActivityRespVO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillActivityDO;
import cn.iocoder.yudao.module.promotion.service.seckill.SeckillActivityService;
import cn.iocoder.yudao.module.promotion.service.seckill.SeckillConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 公开秒杀页不得展示「不在公开商城出售」的商品信息（012 / FR-328 / T112）。
 *
 * 保存入口已经拦过一遍（T109），但「修复前建好的活动」和「先建活动、后建计划」两种顺序
 * 仍然会漏，所以渲染前还要再过滤一次。
 */
@ExtendWith(MockitoExtension.class)
public class AppSeckillActivityControllerTest {

    @Mock
    private SeckillActivityService activityService;
    @Mock
    private SeckillConfigService configService;
    @Mock
    private ProductSpuApi spuApi;
    @Mock
    private ProductSubscriptionPlanApi subscriptionPlanApi;
    @InjectMocks
    private AppSeckillActivityController controller;

    private SeckillActivityDO activity(long id, long spuId) {
        SeckillActivityDO activity = new SeckillActivityDO();
        activity.setId(id);
        activity.setSpuId(spuId);
        return activity;
    }

    private ProductSpuRespDTO spu(long id, String picUrl, int marketPrice) {
        ProductSpuRespDTO dto = new ProductSpuRespDTO();
        dto.setId(id);
        dto.setPicUrl(picUrl);
        dto.setMarketPrice(marketPrice);
        return dto;
    }

    @Test
    void pageDoesNotRenderPlanReferencedSpu() {
        when(activityService.getSeckillActivityAppPageByConfigId(any()))
                .thenReturn(new PageResult<>(List.of(activity(1L, 400L), activity(2L, 200L)), 2L));
        when(activityService.getSeckillProductListByActivityIds(any())).thenReturn(List.of());
        when(spuApi.getSpuList(any())).thenReturn(success(List.of(
                spu(400L, "pic-400", 9900), spu(200L, "pic-200", 100))));
        when(subscriptionPlanApi.getPlanSpuIds(any())).thenReturn(success(List.of(400L)));

        List<AppSeckillActivityRespVO> list = controller
                .getSeckillActivityPage(new AppSeckillActivityPageReqVO()).getData().getList();

        // 活动行还在（不擅自删活动），但会员授权商品的头图/市场价不能下发
        assertEquals(2, list.size());
        assertNull(list.get(0).getPicUrl());
        assertNull(list.get(0).getMarketPrice());
        assertEquals("pic-200", list.get(1).getPicUrl());
        assertEquals(100, list.get(1).getMarketPrice());
    }
}
