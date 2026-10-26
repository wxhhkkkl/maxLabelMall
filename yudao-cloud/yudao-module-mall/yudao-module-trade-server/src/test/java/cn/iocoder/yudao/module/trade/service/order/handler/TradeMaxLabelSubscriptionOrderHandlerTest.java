package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgLicenseGrantReqDTO;
import cn.iocoder.yudao.module.member.api.subscription.MemberSubscriptionApi;
import cn.iocoder.yudao.module.member.api.subscription.dto.MemberSubscriptionGrantReqDTO;
import cn.iocoder.yudao.module.product.api.subscriptionplan.ProductSubscriptionPlanApi;
import cn.iocoder.yudao.module.product.api.subscriptionplan.dto.ProductSubscriptionPlanRespDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TradeMaxLabelSubscriptionOrderHandlerTest {

    @Mock
    private ProductSubscriptionPlanApi planApi;
    @Mock
    private MemberSubscriptionApi subscriptionApi;
    @Mock
    private MemberOrgApi memberOrgApi;
    @InjectMocks
    private TradeMaxLabelSubscriptionOrderHandler handler;

    private TradeOrderDO order() {
        return TradeOrderDO.builder().id(8001L).no("o8001").userId(1001L).build();
    }

    private TradeOrderItemDO item(long id, long skuId, int count) {
        TradeOrderItemDO item = new TradeOrderItemDO();
        item.setId(id);
        item.setSpuId(300L);
        item.setSkuId(skuId);
        item.setCount(count);
        return item;
    }

    private ProductSubscriptionPlanRespDTO plan() {
        ProductSubscriptionPlanRespDTO plan = new ProductSubscriptionPlanRespDTO();
        plan.setId(11L);
        plan.setSpuId(300L);
        plan.setSkuId(301L);
        plan.setPeriodMonths(12);
        plan.setEdition("professional");
        plan.setEnabled(true);
        return plan;
    }

    @Test
    void grantsSubscriptionForMappedSku() {
        when(planApi.getBySkuId(301L)).thenReturn(success(plan()));
        when(subscriptionApi.grant(any())).thenReturn(success(true));

        handler.afterPayOrder(order(), List.of(item(9001L, 301L, 2)));

        ArgumentCaptor<MemberSubscriptionGrantReqDTO> captor = ArgumentCaptor.forClass(MemberSubscriptionGrantReqDTO.class);
        verify(subscriptionApi).grant(captor.capture());
        MemberSubscriptionGrantReqDTO req = captor.getValue();
        assertEquals(1001L, req.getMemberId());
        assertEquals(9001L, req.getOrderItemId());
        assertEquals(12, req.getPeriodMonths());
        assertEquals(2, req.getCount());
        assertEquals("professional", req.getEdition());
    }

    @Test
    void skipsUnmappedSku() {
        when(planApi.getBySkuId(301L)).thenReturn(success(null));
        handler.afterPayOrder(order(), List.of(item(9001L, 301L, 1)));
        verify(subscriptionApi, never()).grant(any());
    }

    @Test
    void grantsEachMappedOrderItem() {
        ProductSubscriptionPlanRespDTO monthly = plan();
        monthly.setSkuId(302L);
        monthly.setPeriodMonths(1);
        when(planApi.getBySkuId(301L)).thenReturn(success(plan()));
        when(planApi.getBySkuId(302L)).thenReturn(success(monthly));
        when(subscriptionApi.grant(any())).thenReturn(success(true));

        handler.afterPayOrder(order(), List.of(item(9001L, 301L, 1), item(9002L, 302L, 1)));

        verify(subscriptionApi, times(2)).grant(any());
    }

    @Test
    void marksRefundsByOrderAndOrderItem() {
        when(subscriptionApi.markRefundedByOrder(8001L)).thenReturn(success(true));
        when(subscriptionApi.markRefundedByOrderItem(9001L)).thenReturn(success(true));

        handler.afterCancelOrder(order(), List.of());
        handler.afterCancelOrderItem(order(), item(9001L, 301L, 1));

        verify(subscriptionApi).markRefundedByOrder(8001L);
        verify(subscriptionApi).markRefundedByOrderItem(9001L);
    }

    @Test
    void grantsEnterpriseLicenseForEnterprisePlan() {
        ProductSubscriptionPlanRespDTO enterprise = plan();
        enterprise.setScope(2);
        enterprise.setEdition("enterprise");
        when(planApi.getBySkuId(301L)).thenReturn(success(enterprise));
        when(memberOrgApi.grantLicense(any())).thenReturn(success(true));

        TradeOrderDO order = order();
        order.setMaxlabelOrgId(88L);
        handler.afterPayOrder(order, List.of(item(9001L, 301L, 2)));

        ArgumentCaptor<MemberOrgLicenseGrantReqDTO> captor =
                ArgumentCaptor.forClass(MemberOrgLicenseGrantReqDTO.class);
        verify(memberOrgApi).grantLicense(captor.capture());
        assertEquals(88L, captor.getValue().getOrgId());
        assertEquals(12, captor.getValue().getPeriodMonths());
        assertEquals(2, captor.getValue().getSeatsTotal());
        assertEquals("enterprise", captor.getValue().getEdition());
        verify(subscriptionApi, never()).grant(any());
    }

    @Test
    void enterprisePlanWithoutOrgSnapshotFails() {
        ProductSubscriptionPlanRespDTO enterprise = plan();
        enterprise.setScope(2);
        when(planApi.getBySkuId(301L)).thenReturn(success(enterprise));

        assertThrows(IllegalStateException.class,
                () -> handler.afterPayOrder(order(), List.of(item(9001L, 301L, 1))));
    }

}
