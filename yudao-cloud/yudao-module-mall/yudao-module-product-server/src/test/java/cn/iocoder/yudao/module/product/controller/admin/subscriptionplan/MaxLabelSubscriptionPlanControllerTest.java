package cn.iocoder.yudao.module.product.controller.admin.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MaxLabelSubscriptionPlanControllerTest {

    @Mock
    private MaxLabelSubscriptionPlanService planService;
    @Mock
    private ProductSpuService spuService;
    @Mock
    private ProductSkuService skuService;
    @InjectMocks
    private MaxLabelSubscriptionPlanController controller;

    @Test
    void pageMapsProductNamePriceAndPeriod() {
        MaxLabelSubscriptionPlanDO plan = MaxLabelSubscriptionPlanDO.builder()
                .id(11L).spuId(300L).skuId(301L).periodMonths(12)
                .enabled(true).sort(1).build();
        MaxLabelSubscriptionPlanPageReqVO req = new MaxLabelSubscriptionPlanPageReqVO();
        req.setPageNo(1);
        req.setPageSize(10);
        PageResult<MaxLabelSubscriptionPlanDO> page = new PageResult<>(List.of(plan), 1L);
        when(planService.getPlanPage(req)).thenReturn(page);
        when(spuService.getSpu(300L)).thenReturn(ProductSpuDO.builder().id(300L).name("MaxLabel 会员授权").build());
        when(skuService.getSku(301L)).thenReturn(ProductSkuDO.builder()
                .id(301L).spuId(300L).price(29900).properties(List.of()).build());

        CommonResult<PageResult<MaxLabelSubscriptionPlanRespVO>> result = controller.getPlanPage(req);
        MaxLabelSubscriptionPlanRespVO vo = result.getData().getList().get(0);
        assertEquals("MaxLabel 会员授权", vo.getSpuName());
        assertEquals(29900, vo.getPrice());
        assertEquals(12, vo.getPeriodMonths());
    }

}
