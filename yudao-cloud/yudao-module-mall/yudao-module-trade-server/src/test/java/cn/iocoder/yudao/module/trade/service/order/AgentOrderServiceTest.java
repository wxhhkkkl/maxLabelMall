package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderPayReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderPayStatusRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderSettlementReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderCreateRespVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link AgentOrderServiceImpl} 单元测试（017）
 */
public class AgentOrderServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AgentOrderServiceImpl agentOrderService;

    @Mock
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Mock
    private TradeOrderQueryService tradeOrderQueryService;
    @Mock
    private MemberUserApi memberUserApi;
    @Mock
    private ProductSkuApi productSkuApi;
    @Mock
    private ProductSpuApi productSpuApi;
    @Mock
    private PayOrderApi payOrderApi;

    @Test
    void customerNotFound() {
        when(memberUserApi.getUser(1L)).thenReturn(CommonResult.<MemberUserRespDTO>success(null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> agentOrderService.settlement(settlementReq(1L, 11L, 1)));
        assertEquals(AGENT_ORDER_CUSTOMER_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void customerDisabled() {
        when(memberUserApi.getUser(1L)).thenReturn(CommonResult.success(user(1L, CommonStatusEnum.DISABLE.getStatus())));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> agentOrderService.settlement(settlementReq(1L, 11L, 1)));
        assertEquals(AGENT_ORDER_CUSTOMER_DISABLED.getCode(), exception.getCode());
    }

    @Test
    void hiddenCustomizationSkuRejected() {
        when(memberUserApi.getUser(1L)).thenReturn(CommonResult.success(user(1L, CommonStatusEnum.ENABLE.getStatus())));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> agentOrderService.settlement(settlementReq(1L, CustomizationConstants.HIDDEN_SKU_ID, 1)));
        assertEquals(AGENT_ORDER_CUSTOMIZATION_NOT_SUPPORTED.getCode(), exception.getCode());
    }

    @Test
    void pickupOnlyProductRejected() {
        when(memberUserApi.getUser(1L)).thenReturn(CommonResult.success(user(1L, CommonStatusEnum.ENABLE.getStatus())));
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(11L, sku(11L, 5L)));
        when(productSpuApi.getSpuMap(any())).thenReturn(Map.of(5L, spu(5L, List.of(2))));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> agentOrderService.settlement(settlementReq(1L, 11L, 1)));
        assertEquals(AGENT_ORDER_PICKUP_NOT_SUPPORTED.getCode(), exception.getCode());
    }

    @Test
    void createForcesCouponAndPointsOffAndUsesSettlementAddress() {
        when(memberUserApi.getUser(1L)).thenReturn(CommonResult.success(user(1L, CommonStatusEnum.ENABLE.getStatus())));
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(11L, sku(11L, 5L)));
        when(productSpuApi.getSpuMap(any())).thenReturn(Map.of(5L, spu(5L, List.of(1))));
        when(tradeOrderUpdateService.settlementOrder(anyLong(), any())).thenReturn(settlement(1, 1024L));
        TradeOrderDO order = TradeOrderDO.builder().id(123L).payPrice(2600).status(0).payOrderId(987L).build();
        when(tradeOrderUpdateService.createOrderByAdmin(anyLong(), anyLong(), any())).thenReturn(order);

        AdminAgentOrderCreateReqVO reqVO = new AdminAgentOrderCreateReqVO();
        reqVO.setUserId(1L);
        reqVO.setItems(List.of(createItem(11L, 2)));
        reqVO.setUserRemark("请在工作日送");
        TradeOrderCreateRespVO resp = agentOrderService.create(9L, reqVO);

        // 创建请求：券/积分关闭、地址用结算结果、留言透传
        ArgumentCaptor<AppTradeOrderCreateReqVO> createCaptor = ArgumentCaptor.forClass(AppTradeOrderCreateReqVO.class);
        verify(tradeOrderUpdateService).createOrderByAdmin(eq(9L), eq(1L), createCaptor.capture());
        AppTradeOrderCreateReqVO createReq = createCaptor.getValue();
        assertNull(createReq.getCouponId());
        assertFalse(createReq.getPointStatus());
        assertNull(createReq.getCustomization());
        assertEquals(1024L, createReq.getAddressId());
        assertEquals("请在工作日送", createReq.getRemark());
        assertEquals(1, createReq.getItems().size());
        // 结算请求同样不读券/积分
        ArgumentCaptor<AppTradeOrderSettlementReqVO> settlementCaptor = ArgumentCaptor.forClass(AppTradeOrderSettlementReqVO.class);
        verify(tradeOrderUpdateService).settlementOrder(eq(1L), settlementCaptor.capture());
        assertNull(settlementCaptor.getValue().getCouponId());
        assertFalse(settlementCaptor.getValue().getPointStatus());

        assertEquals(123L, resp.getOrderId());
        assertEquals(987L, resp.getPayOrderId());
        assertEquals(2600, resp.getPayPrice());
    }

    @Test
    void createOfflinePassesPayChannelCodeThrough() {
        when(memberUserApi.getUser(1L)).thenReturn(CommonResult.success(user(1L, CommonStatusEnum.ENABLE.getStatus())));
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(11L, sku(11L, 5L)));
        when(productSpuApi.getSpuMap(any())).thenReturn(Map.of(5L, spu(5L, List.of(3))));
        when(tradeOrderUpdateService.settlementOrder(anyLong(), any())).thenReturn(settlement(3, null));
        TradeOrderDO order = TradeOrderDO.builder().id(123L).payPrice(2600).status(0).build();
        when(tradeOrderUpdateService.createOrderByAdmin(anyLong(), anyLong(), any())).thenReturn(order);

        AdminAgentOrderCreateReqVO reqVO = new AdminAgentOrderCreateReqVO();
        reqVO.setUserId(1L);
        reqVO.setItems(List.of(createItem(11L, 1)));
        reqVO.setPayChannelCode("offline");
        TradeOrderCreateRespVO resp = agentOrderService.create(9L, reqVO);

        ArgumentCaptor<AppTradeOrderCreateReqVO> captor = ArgumentCaptor.forClass(AppTradeOrderCreateReqVO.class);
        verify(tradeOrderUpdateService).createOrderByAdmin(eq(9L), eq(1L), captor.capture());
        assertEquals("offline", captor.getValue().getPayChannelCode());
        assertNull(resp.getPayOrderId());
    }

    @Test
    void settlementAllowsMissingAddressForPreview() {
        when(memberUserApi.getUser(1L)).thenReturn(CommonResult.success(user(1L, CommonStatusEnum.ENABLE.getStatus())));
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(11L, sku(11L, 5L)));
        when(productSpuApi.getSpuMap(any())).thenReturn(Map.of(5L, spu(5L, List.of(1))));
        when(tradeOrderUpdateService.settlementOrder(anyLong(), any())).thenReturn(settlement(1, null));

        // 预览没有地址也要返回，页面才能渲染地址选择卡片
        AppTradeOrderSettlementRespVO resp = agentOrderService.settlement(settlementReq(1L, 11L, 1));
        assertEquals(1, resp.getDeliveryType());
        assertNull(resp.getAddress());
    }

    @Test
    void createExpressOrderWithoutAddressRejected() {
        when(memberUserApi.getUser(1L)).thenReturn(CommonResult.success(user(1L, CommonStatusEnum.ENABLE.getStatus())));
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(11L, sku(11L, 5L)));
        when(productSpuApi.getSpuMap(any())).thenReturn(Map.of(5L, spu(5L, List.of(1))));
        when(tradeOrderUpdateService.settlementOrder(anyLong(), any())).thenReturn(settlement(1, null));

        AdminAgentOrderCreateReqVO reqVO = new AdminAgentOrderCreateReqVO();
        reqVO.setUserId(1L);
        reqVO.setItems(List.of(createItem(11L, 1)));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> agentOrderService.create(9L, reqVO));
        assertEquals(AGENT_ORDER_ADDRESS_REQUIRED.getCode(), exception.getCode());
    }

    @Test
    void payRejectsNonWhitelistChannel() {
        AdminAgentOrderPayReqVO reqVO = new AdminAgentOrderPayReqVO();
        reqVO.setOrderId(1L);
        reqVO.setChannelCode("mock");
        ServiceException exception = assertThrows(ServiceException.class,
                () -> agentOrderService.pay(reqVO));
        assertEquals(AGENT_ORDER_CHANNEL_NOT_SUPPORTED.getCode(), exception.getCode());
    }

    @Test
    void getPayStatusRejectsSelfOrder() {
        when(tradeOrderQueryService.getOrder(1L)).thenReturn(TradeOrderDO.builder()
                .id(1L).status(TradeOrderStatusEnum.UNPAID.getStatus()).payStatus(false).payOrderId(2L).build());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> agentOrderService.getPayStatus(1L));
        assertEquals(AGENT_ORDER_PAY_FAIL_NOT_AGENT_ORDER.getCode(), exception.getCode());
    }

    @Test
    void getPayStatusSyncsWhenPayOrderSuccess() {
        TradeOrderDO waiting = TradeOrderDO.builder().id(1L).agentUserId(9L)
                .status(TradeOrderStatusEnum.UNPAID.getStatus()).payStatus(false).payOrderId(2L).build();
        TradeOrderDO paid = TradeOrderDO.builder().id(1L).agentUserId(9L)
                .status(TradeOrderStatusEnum.UNDELIVERED.getStatus()).payStatus(true).payOrderId(2L).build();
        // 第一次查是待支付；同步后第二次查是待发货
        when(tradeOrderQueryService.getOrder(1L)).thenReturn(waiting, paid);
        PayOrderRespDTO payOrder = new PayOrderRespDTO();
        payOrder.setId(2L);
        payOrder.setStatus(PayOrderStatusEnum.SUCCESS.getStatus());
        when(payOrderApi.getOrder(2L)).thenReturn(CommonResult.success(payOrder));

        AdminAgentOrderPayStatusRespVO resp = agentOrderService.getPayStatus(1L);
        verify(tradeOrderUpdateService).syncOrderPayStatusQuietly(1L, 2L);
        assertTrue(resp.getPayStatus());
        assertEquals(TradeOrderStatusEnum.UNDELIVERED.getStatus(), resp.getOrderStatus());
    }

    @Test
    void payRejectsSelfOrder() {
        when(tradeOrderQueryService.getOrder(1L)).thenReturn(TradeOrderDO.builder()
                .id(1L).status(TradeOrderStatusEnum.UNPAID.getStatus()).payStatus(false).payOrderId(2L).build());
        AdminAgentOrderPayReqVO reqVO = new AdminAgentOrderPayReqVO();
        reqVO.setOrderId(1L);
        reqVO.setChannelCode("wx_native");
        ServiceException exception = assertThrows(ServiceException.class,
                () -> agentOrderService.pay(reqVO));
        assertEquals(AGENT_ORDER_PAY_FAIL_NOT_AGENT_ORDER.getCode(), exception.getCode());
    }

    // ==================== 构造 ====================

    private static MemberUserRespDTO user(Long id, Integer status) {
        MemberUserRespDTO user = new MemberUserRespDTO();
        user.setId(id);
        user.setStatus(status);
        return user;
    }

    private static ProductSkuRespDTO sku(Long id, Long spuId) {
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(id);
        sku.setSpuId(spuId);
        return sku;
    }

    private static ProductSpuRespDTO spu(Long id, List<Integer> deliveryTypes) {
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setId(id);
        spu.setDeliveryTypes(deliveryTypes);
        return spu;
    }

    private static AppTradeOrderSettlementRespVO settlement(Integer deliveryType, Long addressId) {
        AppTradeOrderSettlementRespVO resp = new AppTradeOrderSettlementRespVO();
        resp.setDeliveryType(deliveryType);
        if (addressId != null) {
            AppTradeOrderSettlementRespVO.Address address = new AppTradeOrderSettlementRespVO.Address();
            address.setId(addressId);
            resp.setAddress(address);
        }
        return resp;
    }

    private static AdminAgentOrderSettlementReqVO settlementReq(Long userId, Long skuId, Integer count) {
        AdminAgentOrderSettlementReqVO reqVO = new AdminAgentOrderSettlementReqVO();
        reqVO.setUserId(userId);
        AdminAgentOrderSettlementReqVO.Item item = new AdminAgentOrderSettlementReqVO.Item();
        item.setSkuId(skuId);
        item.setCount(count);
        reqVO.setItems(List.of(item));
        return reqVO;
    }

    private static AdminAgentOrderCreateReqVO.Item createItem(Long skuId, Integer count) {
        AdminAgentOrderCreateReqVO.Item item = new AdminAgentOrderCreateReqVO.Item();
        item.setSkuId(skuId);
        item.setCount(count);
        return item;
    }

}
