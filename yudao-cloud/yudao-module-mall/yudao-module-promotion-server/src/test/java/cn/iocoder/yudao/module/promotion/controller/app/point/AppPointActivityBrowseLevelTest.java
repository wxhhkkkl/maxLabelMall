package cn.iocoder.yudao.module.promotion.controller.app.point;

import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.product.api.subscriptionplan.ProductSubscriptionPlanApi;
import cn.iocoder.yudao.module.promotion.controller.app.point.vo.AppPointActivityRespVO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.point.PointActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.point.PointProductDO;
import cn.iocoder.yudao.module.promotion.service.point.PointActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 积分商城活动商品的浏览等级过滤（019 / FR-528）：与其它活动不同的 spuMap 写法。
 */
@ExtendWith(MockitoExtension.class)
public class AppPointActivityBrowseLevelTest {

    @Mock
    private PointActivityService pointActivityService;
    @Mock
    private ProductSpuApi productSpuApi;
    @Mock
    private ProductSubscriptionPlanApi subscriptionPlanApi;
    @InjectMocks
    private AppPointActivityController controller;

    @Test
    void enterpriseOnlySpuDoesNotRenderProductInfo() {
        PointActivityDO activity = new PointActivityDO().setId(1L).setSpuId(3L);
        PointProductDO product = new PointProductDO()
                .setActivityId(1L).setSpuId(3L).setPoint(10).setPrice(100);
        when(pointActivityService.getPointProductListByActivityIds(any())).thenReturn(List.of(product));
        Map<Long, ProductSpuRespDTO> spuMap = new HashMap<>();
        spuMap.put(3L, new ProductSpuRespDTO().setId(3L).setName("企业商品").setEnterpriseOnly(true));
        when(productSpuApi.getSpuMap(any())).thenReturn(spuMap);
        when(subscriptionPlanApi.getPlanSpuIds(any())).thenReturn(success(List.of()));
        when(productSpuApi.getVisibleSpuIds(isNull(), any())).thenReturn(success(List.of(3L)));

        List<AppPointActivityRespVO> result = controller.buildAppPointActivityRespVOList(List.of(activity));

        assertEquals(1, result.size());
        assertNull(result.get(0).getSpuName());
    }

    @Test
    void hiddenSpuDoesNotRenderProductInfo() {
        PointActivityDO activity = new PointActivityDO().setId(1L).setSpuId(3L);
        PointProductDO product = new PointProductDO()
                .setActivityId(1L).setSpuId(3L).setPoint(10).setPrice(100);
        when(pointActivityService.getPointProductListByActivityIds(any())).thenReturn(List.of(product));
        Map<Long, ProductSpuRespDTO> spuMap = new HashMap<>();
        spuMap.put(3L, new ProductSpuRespDTO().setId(3L).setName("隐藏商品")
                .setPicUrl("https://example.com/p.png").setMarketPrice(200));
        when(productSpuApi.getSpuMap(any())).thenReturn(spuMap);
        when(subscriptionPlanApi.getPlanSpuIds(any())).thenReturn(success(List.of()));
        when(productSpuApi.getVisibleSpuIds(isNull(), any())).thenReturn(success(List.of()));

        List<AppPointActivityRespVO> result = controller.buildAppPointActivityRespVOList(List.of(activity));

        assertEquals(1, result.size());
        assertNull(result.get(0).getSpuName());
        assertNull(result.get(0).getPicUrl());
    }

}
