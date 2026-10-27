package cn.iocoder.yudao.module.promotion.controller.admin.seckill;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.product.api.subscriptionplan.ProductSubscriptionPlanApi;
import cn.iocoder.yudao.module.promotion.controller.admin.seckill.vo.activity.SeckillActivityCreateReqVO;
import cn.iocoder.yudao.module.promotion.service.seckill.SeckillActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_ENTERPRISE_ONLY;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_SUBSCRIPTION_ONLY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 公开促销活动不得挂「不在公开商城出售」的商品（012 / R66 / FR-328）。
 *
 * 两类都要拦：企业专享商品（`enterprise_only=1`），以及被会员授权计划引用的商品
 * （`enterprise_only=0`，但只从个人续费页/企业采购页出售）。
 */
@ExtendWith(MockitoExtension.class)
public class SeckillActivityControllerTest {

    @Mock
    private SeckillActivityService seckillActivityService;
    @Mock
    private ProductSpuApi productSpuApi;
    @Mock
    private ProductSubscriptionPlanApi subscriptionPlanApi;
    @InjectMocks
    private SeckillActivityController controller;

    private SeckillActivityCreateReqVO req(Long spuId) {
        SeckillActivityCreateReqVO reqVO = new SeckillActivityCreateReqVO();
        reqVO.setSpuId(spuId);
        return reqVO;
    }

    private ProductSpuRespDTO spu(long id, boolean enterpriseOnly) {
        ProductSpuRespDTO dto = new ProductSpuRespDTO();
        dto.setId(id);
        dto.setEnterpriseOnly(enterpriseOnly);
        return dto;
    }

    @Test
    void enterpriseSpuRejected() {
        when(productSpuApi.getSpuList(any())).thenReturn(success(List.of(spu(300L, true))));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> controller.createSeckillActivity(req(300L)));
        assertEquals(SPU_ENTERPRISE_ONLY.getCode(), exception.getCode());
    }

    @Test
    void planReferencedSpuRejected() {
        // 个人授权商品：enterprise_only=0，但被 maxlabel_subscription_plan 引用
        when(productSpuApi.getSpuList(any())).thenReturn(success(List.of(spu(400L, false))));
        when(subscriptionPlanApi.getPlanSpuIds(any())).thenReturn(success(List.of(400L)));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> controller.createSeckillActivity(req(400L)));
        assertEquals(SPU_SUBSCRIPTION_ONLY.getCode(), exception.getCode());
    }

    @Test
    void ordinarySpuAllowed() {
        when(productSpuApi.getSpuList(any())).thenReturn(success(List.of(spu(200L, false))));
        when(subscriptionPlanApi.getPlanSpuIds(any())).thenReturn(success(List.of()));

        assertDoesNotThrow(() -> controller.createSeckillActivity(req(200L)));
    }
}
