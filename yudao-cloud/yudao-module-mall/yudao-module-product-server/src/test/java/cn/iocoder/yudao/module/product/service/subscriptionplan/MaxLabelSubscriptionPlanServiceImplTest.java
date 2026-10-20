package cn.iocoder.yudao.module.product.service.subscriptionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanCreateReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;
import cn.iocoder.yudao.module.product.dal.mysql.subscriptionplan.MaxLabelSubscriptionPlanMapper;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MaxLabelSubscriptionPlanServiceImplTest {

    @Mock
    private MaxLabelSubscriptionPlanMapper planMapper;
    @Mock
    private ProductSpuService spuService;
    @Mock
    private ProductSkuService skuService;
    @InjectMocks
    private MaxLabelSubscriptionPlanServiceImpl planService;

    private ProductSpuDO virtualSpu;
    private ProductSkuDO sku;

    @BeforeEach
    void setUp() {
        virtualSpu = ProductSpuDO.builder()
                .id(300L).name("MaxLabel 会员授权").deliveryTypes(List.of(3)).build();
        sku = ProductSkuDO.builder()
                .id(301L).spuId(300L).price(2900).build();
    }

    private MaxLabelSubscriptionPlanCreateReqVO request() {
        MaxLabelSubscriptionPlanCreateReqVO req = new MaxLabelSubscriptionPlanCreateReqVO();
        req.setSpuId(300L);
        req.setSkuId(301L);
        req.setPeriodMonths(1);
        req.setEnabled(true);
        req.setSort(1);
        return req;
    }

    @Test
    void createsPlanForVirtualSku() {
        when(skuService.getSku(301L)).thenReturn(sku);
        when(spuService.getSpu(300L)).thenReturn(virtualSpu);
        when(planMapper.selectBySkuId(301L)).thenReturn(null);
        when(planMapper.selectEnabledList()).thenReturn(List.of());
        when(planMapper.insert(any(MaxLabelSubscriptionPlanDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MaxLabelSubscriptionPlanDO.class).setId(11L);
            return 1;
        });

        assertEquals(11L, planService.createPlan(request()));
    }

    @Test
    void rejectsNonVirtualSpu() {
        virtualSpu.setDeliveryTypes(List.of(1));
        when(skuService.getSku(301L)).thenReturn(sku);
        when(spuService.getSpu(300L)).thenReturn(virtualSpu);

        ServiceException exception = assertThrows(ServiceException.class, () -> planService.createPlan(request()));
        assertEquals(MEMBER_SUBSCRIPTION_PLAN_NOT_VIRTUAL.getCode(), exception.getCode());
    }

    @Test
    void rejectsZeroPrice() {
        sku.setPrice(0);
        when(skuService.getSku(301L)).thenReturn(sku);
        when(spuService.getSpu(300L)).thenReturn(virtualSpu);

        ServiceException exception = assertThrows(ServiceException.class, () -> planService.createPlan(request()));
        assertEquals(MEMBER_SUBSCRIPTION_PLAN_PRICE_ZERO.getCode(), exception.getCode());
    }

    @Test
    void rejectsSecondEnabledSpu() {
        when(skuService.getSku(301L)).thenReturn(sku);
        when(spuService.getSpu(300L)).thenReturn(virtualSpu);
        when(planMapper.selectBySkuId(301L)).thenReturn(null);
        when(planMapper.selectEnabledList()).thenReturn(List.of(
                MaxLabelSubscriptionPlanDO.builder().id(1L).spuId(999L).build()));

        ServiceException exception = assertThrows(ServiceException.class, () -> planService.createPlan(request()));
        assertEquals(MEMBER_SUBSCRIPTION_PLAN_MULTI_SPU.getCode(), exception.getCode());
    }

    @Test
    void rejectsDuplicateSku() {
        when(skuService.getSku(301L)).thenReturn(sku);
        when(spuService.getSpu(300L)).thenReturn(virtualSpu);
        when(planMapper.selectBySkuId(301L)).thenReturn(
                MaxLabelSubscriptionPlanDO.builder().id(2L).skuId(301L).build());

        ServiceException exception = assertThrows(ServiceException.class, () -> planService.createPlan(request()));
        assertEquals(MEMBER_SUBSCRIPTION_PLAN_DUPLICATE.getCode(), exception.getCode());
    }

}
