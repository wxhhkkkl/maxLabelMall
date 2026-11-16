package cn.iocoder.yudao.module.promotion.controller.app.seckill;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.product.api.subscriptionplan.ProductSubscriptionPlanApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 秒杀活动商品的浏览等级过滤（019 / FR-528）
 */
@ExtendWith(MockitoExtension.class)
public class AppSeckillActivityBrowseLevelTest {

    @Mock
    private ProductSpuApi spuApi;
    @Mock
    private ProductSubscriptionPlanApi subscriptionPlanApi;
    @InjectMocks
    private AppSeckillActivityController controller;

    private ProductSpuRespDTO spu(long id, String name) {
        return new ProductSpuRespDTO().setId(id).setName(name);
    }

    @Test
    void guestOnlyKeepsPublicSpu() {
        when(spuApi.getSpuList(any())).thenReturn(success(List.of(spu(1L, "公开"), spu(2L, "登录"))));
        when(subscriptionPlanApi.getPlanSpuIds(any())).thenReturn(success(List.of()));
        when(spuApi.getVisibleSpuIds(isNull(), any())).thenReturn(success(List.of(1L)));

        var result = controller.getPublicSpuList(List.of(1L, 2L));
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void enterpriseOnlySpuIsFilteredEvenWhenBrowseVisible() {
        ProductSpuRespDTO enterprise = spu(3L, "企业商品").setEnterpriseOnly(true);
        when(spuApi.getSpuList(any())).thenReturn(success(List.of(spu(1L, "公开"), enterprise)));
        when(subscriptionPlanApi.getPlanSpuIds(any())).thenReturn(success(List.of()));
        when(spuApi.getVisibleSpuIds(isNull(), any())).thenReturn(success(List.of(1L, 3L)));

        var result = controller.getPublicSpuList(List.of(1L, 3L));
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void memberKeepsLoginSpu() {
        when(spuApi.getSpuList(any())).thenReturn(success(List.of(spu(1L, "公开"), spu(2L, "登录"))));
        when(subscriptionPlanApi.getPlanSpuIds(any())).thenReturn(success(List.of()));
        when(spuApi.getVisibleSpuIds(eq(1001L), any())).thenReturn(success(List.of(1L, 2L)));

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var result = controller.getPublicSpuList(List.of(1L, 2L));
            assertEquals(2, result.size());
        }
    }

}
