package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.pay.api.refund.PayRefundApi;
import cn.iocoder.yudao.module.promotion.api.combination.CombinationRecordApi;
import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.yudao.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.yudao.module.trade.enums.aftersale.AfterSaleWayEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderItemAfterSaleStatusEnum;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.AGENT_AFTER_SALE_FAIL_NOT_AGENT_ORDER;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.AGENT_ORDER_AFTER_SALE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ENTERPRISE_ORDER_REFUND_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.SUBSCRIPTION_ORDER_REFUND_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link AfterSaleServiceImpl} 的单元测试 —— 只钉这一条：
 * **企业授权 / 个人会员订阅订单在用户端不允许申请退款**（Q79 / 2026-10-27）。
 *
 * 运营后台的人工退款不经过 `createAfterSale`，所以这里只验用户端入口。
 */
@Import(AfterSaleServiceImpl.class)
public class AfterSaleServiceImplTest extends BaseDbUnitTest {

    @Resource
    private AfterSaleServiceImpl afterSaleService;

    @MockitoBean
    private TradeOrderUpdateService tradeOrderUpdateService;
    @MockitoBean
    private TradeOrderQueryService tradeOrderQueryService;
    @MockitoBean
    private DeliveryExpressService deliveryExpressService;
    @MockitoBean
    private AfterSaleMapper tradeAfterSaleMapper;
    @MockitoBean
    private TradeNoRedisDAO tradeNoRedisDAO;
    @MockitoBean
    private PayRefundApi payRefundApi;
    @MockitoBean
    private CombinationRecordApi combinationRecordApi;
    @MockitoBean
    private TradeOrderProperties tradeOrderProperties;

    @Test
    void enterpriseOrderRefundRejected() {
        // 订单项：未申请过售后、实付 1000，refundPrice 100 能过金额校验
        when(tradeOrderQueryService.getOrderItem(3L, 1L)).thenReturn(
                item(1L, 2L, 1000, TradeOrderItemAfterSaleStatusEnum.NONE.getStatus()));
        // 订单：带企业快照 → 用户端退款一律拒绝（先于状态校验）
        when(tradeOrderQueryService.getOrder(3L, 2L)).thenReturn(
                TradeOrderDO.builder().id(2L).maxlabelOrgId(9L).build());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> afterSaleService.createAfterSale(3L, request()));
        assertEquals(ENTERPRISE_ORDER_REFUND_NOT_SUPPORTED.getCode(), exception.getCode());
    }

    @Test
    void subscriptionOrderRefundRejected() {
        when(tradeOrderQueryService.getOrderItem(3L, 1L)).thenReturn(
                item(1L, 2L, 1000, TradeOrderItemAfterSaleStatusEnum.NONE.getStatus()));
        // 订单：个人会员订阅快照 → 用户端退款同样拒绝
        when(tradeOrderQueryService.getOrder(3L, 2L)).thenReturn(
                TradeOrderDO.builder().id(2L).maxlabelSubscription(true).build());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> afterSaleService.createAfterSale(3L, request()));
        assertEquals(SUBSCRIPTION_ORDER_REFUND_NOT_SUPPORTED.getCode(), exception.getCode());
    }

    @Test
    void agentOrderRefundRejectedForMember() {
        when(tradeOrderQueryService.getOrderItem(3L, 1L)).thenReturn(
                item(1L, 2L, 1000, TradeOrderItemAfterSaleStatusEnum.NONE.getStatus()));
        // 订单：代客下单快照 → 会员侧退款拒绝（017 / Q126）
        when(tradeOrderQueryService.getOrder(3L, 2L)).thenReturn(
                TradeOrderDO.builder().id(2L).userId(3L).agentUserId(1L).build());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> afterSaleService.createAfterSale(3L, request()));
        assertEquals(AGENT_ORDER_AFTER_SALE_NOT_SUPPORTED.getCode(), exception.getCode());
    }

    @Test
    void agentAfterSaleRejectsSelfOrder() {
        when(tradeOrderQueryService.getOrderItem(1L)).thenReturn(
                item(1L, 2L, 1000, TradeOrderItemAfterSaleStatusEnum.NONE.getStatus()));
        // 客户自助订单：不能走后台代客售后入口
        when(tradeOrderQueryService.getOrder(2L)).thenReturn(
                TradeOrderDO.builder().id(2L).userId(3L).build());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> afterSaleService.createAfterSaleByAdmin(9L, request()));
        assertEquals(AGENT_AFTER_SALE_FAIL_NOT_AGENT_ORDER.getCode(), exception.getCode());
    }

    @Test
    void agentAfterSaleAllowsAgentOrder() {
        TradeOrderItemDO item = item(1L, 2L, 1000, TradeOrderItemAfterSaleStatusEnum.NONE.getStatus());
        item.setUserId(3L);
        when(tradeOrderQueryService.getOrderItem(1L)).thenReturn(item);
        TradeOrderDO order = TradeOrderDO.builder().id(2L).userId(3L).agentUserId(9L)
                .status(cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum.UNDELIVERED.getStatus())
                .payStatus(true).build();
        when(tradeOrderQueryService.getOrder(2L)).thenReturn(order);
        when(tradeOrderQueryService.getOrder(3L, 2L)).thenReturn(order);
        when(tradeNoRedisDAO.generate(any())).thenReturn("NO-AGENT-AS");

        // 后台代客入口跳过用户端守卫，不应抛「代客订单不支持退款」
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> afterSaleService.createAfterSaleByAdmin(9L, request()));
    }

    private static TradeOrderItemDO item(Long id, Long orderId, Integer payPrice, Integer afterSaleStatus) {
        TradeOrderItemDO item = new TradeOrderItemDO();
        item.setId(id);
        item.setOrderId(orderId);
        item.setPayPrice(payPrice);
        item.setAfterSaleStatus(afterSaleStatus);
        return item;
    }

    private static AppAfterSaleCreateReqVO request() {
        AppAfterSaleCreateReqVO reqVO = new AppAfterSaleCreateReqVO();
        reqVO.setOrderItemId(1L);
        reqVO.setWay(AfterSaleWayEnum.REFUND.getWay());
        reqVO.setRefundPrice(100);
        reqVO.setApplyReason("不想要了");
        return reqVO;
    }

}
