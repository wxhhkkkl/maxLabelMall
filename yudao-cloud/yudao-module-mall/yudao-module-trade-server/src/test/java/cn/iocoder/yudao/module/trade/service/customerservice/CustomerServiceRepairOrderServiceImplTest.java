package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.api.refund.PayRefundApi;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceLogMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceRepairOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceRepairRequestMapper;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderPayStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairStatusEnum;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceOfflinePayInfoBO;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceRepairCreateReqBO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 维修开单 / 联系 / 取消（021 / US72）
 */
@Import({CustomerServiceRepairOrderServiceImpl.class, CustomerServiceRepairRequestServiceImpl.class,
        CustomerServiceLogServiceImpl.class, CustomerServiceFileValidator.class})
public class CustomerServiceRepairOrderServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CustomerServiceRepairOrderService customerServiceRepairOrderService;
    @Resource
    private CustomerServiceRepairRequestService customerServiceRepairRequestService;
    @Resource
    private CustomerServiceRepairOrderMapper customerServiceRepairOrderMapper;
    @Resource
    private CustomerServiceRepairRequestMapper customerServiceRepairRequestMapper;
    @Resource
    private CustomerServiceLogMapper customerServiceLogMapper;
    @MockitoBean
    private PayOrderApi payOrderApi;
    @MockitoBean
    private PayRefundApi payRefundApi;
    @MockitoBean
    private TradeOrderProperties tradeOrderProperties;
    @MockitoBean
    private TradeConfigService tradeConfigService;

    @BeforeEach
    public void setUpPayMocks() {
        when(tradeOrderProperties.getPayAppKey()).thenReturn("mall");
        when(tradeOrderProperties.getPayExpireTime()).thenReturn(Duration.ofHours(2));
        when(payOrderApi.createOrder(any())).thenReturn(CommonResult.success(99001L));
        when(payOrderApi.createPaidOfflineOrder(any())).thenReturn(CommonResult.success(99002L));
        PayOrderRespDTO payOrder = new PayOrderRespDTO();
        payOrder.setId(99001L);
        payOrder.setChannelCode("wx_lite");
        when(payOrderApi.getOrder(99001L)).thenReturn(CommonResult.success(payOrder));
        when(payRefundApi.createRefund(any())).thenReturn(CommonResult.success(88001L));
        PayRefundRespDTO payRefund = new PayRefundRespDTO();
        payRefund.setId(88001L);
        payRefund.setStatus(PayRefundStatusEnum.SUCCESS.getStatus());
        when(payRefundApi.getRefund(88001L)).thenReturn(CommonResult.success(payRefund));
    }

    private TradeConfigDO offlineConfig(boolean enabled) {
        TradeConfigDO config = new TradeConfigDO();
        config.setOfflinePayEnabled(enabled);
        config.setOfflinePayAccountName("北京御旺宸发科技有限公司");
        config.setOfflinePayAccountNo("1234567890");
        config.setOfflinePayBankName("工商银行");
        config.setOfflinePayTip("请备注维修单号");
        return config;
    }

    private Long createRepair(Long memberId) {
        CustomerServiceRepairCreateReqBO reqBO = new CustomerServiceRepairCreateReqBO();
        reqBO.setMemberId(memberId);
        reqBO.setDeviceModel("Zebra ZT411");
        reqBO.setFaultDesc("走纸不停");
        reqBO.setContactMobile("13800008888");
        reqBO.setReceiverName("张三");
        reqBO.setReceiverMobile("13800008888");
        reqBO.setReceiverArea("江苏省 南京市");
        reqBO.setReceiverDetail("xx 路 1 号");
        return customerServiceRepairRequestService.createRepair(reqBO);
    }

    @Test
    public void testCreateOrder_payableAndStatus() {
        Long requestId = createRepair(88L);

        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "更换打印头，含寄回运费", 1L);

        CustomerServiceRepairOrderDO order = customerServiceRepairOrderMapper.selectById(orderId);
        assertEquals(12000, order.getPayableAmount());
        assertEquals(CustomerServiceRepairOrderPayStatusEnum.UNPAID.getStatus(), order.getPayStatus());
        assertEquals(88L, order.getMemberId());
        assertEquals(CustomerServiceRepairStatusEnum.ORDERED.getStatus(),
                customerServiceRepairRequestMapper.selectById(requestId).getStatus());
    }

    @Test
    public void testCreateOrder_zeroFee_noPay() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 0, 0, "保内免费", 1L);

        assertEquals(CustomerServiceRepairOrderPayStatusEnum.NO_PAY.getStatus(),
                customerServiceRepairOrderMapper.selectById(orderId).getPayStatus());
    }

    @Test
    public void testCreateOrder_oneOrderPerRequest() {
        Long requestId = createRepair(88L);
        customerServiceRepairOrderService.createOrder(requestId, 100, 0, "维修费", 1L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.createOrder(requestId, 200, 0, "再开一单", 1L));
        assertEquals(CUSTOMER_SERVICE_REPAIR_ORDER_EXISTS.getCode(), ex.getCode());
        assertEquals(1, customerServiceRepairOrderMapper.selectList().size());
    }

    @Test
    public void testCreateOrder_invalidFeesRejected() {
        Long requestId = createRepair(88L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.createOrder(requestId, -1, 0, "维修费", 1L));
        assertEquals(CUSTOMER_SERVICE_REPAIR_ORDER_INVALID.getCode(), ex.getCode());

        ServiceException blankRemark = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.createOrder(requestId, 100, 0, " ", 1L));
        assertEquals(CUSTOMER_SERVICE_REPAIR_ORDER_INVALID.getCode(), blankRemark.getCode());
    }

    @Test
    public void testRecordContact_movesToContacted() {
        Long requestId = createRepair(88L);

        customerServiceRepairOrderService.recordContact(requestId, "已电话联系，确认可寄修", 1L);

        CustomerServiceRepairRequestDO request = customerServiceRepairRequestMapper.selectById(requestId);
        assertEquals(CustomerServiceRepairStatusEnum.CONTACTED.getStatus(), request.getStatus());
        assertEquals("已电话联系，确认可寄修", request.getContactResult());
        assertNotNull(request.getContactTime());
    }

    @Test
    public void testCancel_unpaidOrderCanceled() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 100, 20, "维修费", 1L);

        customerServiceRepairOrderService.cancel(requestId, "客户放弃维修", 1L);

        CustomerServiceRepairRequestDO request = customerServiceRepairRequestMapper.selectById(requestId);
        assertEquals(CustomerServiceRepairStatusEnum.CANCELED.getStatus(), request.getStatus());
        assertEquals("客户放弃维修", request.getCancelReason());
        assertEquals(CustomerServiceRepairOrderPayStatusEnum.CANCELED.getStatus(),
                customerServiceRepairOrderMapper.selectById(orderId).getPayStatus());
    }

    @Test
    public void testCancel_paidOrderRequiresRefund() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 100, 20, "维修费", 1L);
        CustomerServiceRepairOrderDO paid = new CustomerServiceRepairOrderDO();
        paid.setId(orderId);
        paid.setPayStatus(CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus());
        customerServiceRepairOrderMapper.updateById(paid);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.cancel(requestId, "客户放弃维修", 1L));
        assertEquals(CUSTOMER_SERVICE_REPAIR_ORDER_PAID_REFUND_FIRST.getCode(), ex.getCode());
    }

    @Test
    public void testCancel_blankReasonRejected() {
        Long requestId = createRepair(88L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.cancel(requestId, " ", 1L));
        assertEquals(CUSTOMER_SERVICE_REPAIR_CANCEL_REASON_REQUIRED.getCode(), ex.getCode());
    }

    // ==================== 支付 / 寄出（US73） ====================

    @Test
    public void testGetListByRepairRequestIds_batch() {
        Long requestA = createRepair(88L);
        Long requestB = createRepair(88L);
        customerServiceRepairOrderService.createOrder(requestA, 100, 0, "A", 1L);
        customerServiceRepairOrderService.createOrder(requestB, 200, 0, "B", 1L);

        assertEquals(2, customerServiceRepairOrderService.getListByRepairRequestIds(
                java.util.List.of(requestA, requestB)).size());
        assertEquals(0, customerServiceRepairOrderService.getListByRepairRequestIds(
                java.util.List.of()).size());
    }

    @Test
    public void testPayOnline_createsAndReusesPayOrder() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);

        Long payOrderId = customerServiceRepairOrderService.payOnline(orderId, 88L);
        assertEquals(99001L, payOrderId);
        assertEquals(99001L, customerServiceRepairOrderMapper.selectById(orderId).getPayOrderId());

        assertEquals(99001L, customerServiceRepairOrderService.payOnline(orderId, 88L));
        verify(payOrderApi, times(1)).createOrder(any());
    }

    @Test
    public void testUpdateOrderPaid_idempotent() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);

        customerServiceRepairOrderService.updateOrderPaid(orderId, 99001L);
        CustomerServiceRepairOrderDO paid = customerServiceRepairOrderMapper.selectById(orderId);
        assertEquals(CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus(), paid.getPayStatus());
        assertEquals("wx_lite", paid.getPayChannelCode());
        assertNotNull(paid.getPayTime());

        // 重复回调：不报错、不重复改
        customerServiceRepairOrderService.updateOrderPaid(orderId, 99001L);
    }

    @Test
    public void testChooseOffline_returnsInfoAndStatus() {
        when(tradeConfigService.getTradeConfig()).thenReturn(offlineConfig(true));
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);

        CustomerServiceOfflinePayInfoBO info = customerServiceRepairOrderService.chooseOffline(orderId, 88L);

        assertEquals(CustomerServiceRepairOrderPayStatusEnum.WAITING_CONFIRM.getStatus(),
                customerServiceRepairOrderMapper.selectById(orderId).getPayStatus());
        assertEquals("北京御旺宸发科技有限公司", info.getAccountName());
    }

    @Test
    public void testChooseOffline_idempotentWhenWaitingConfirm() {
        when(tradeConfigService.getTradeConfig()).thenReturn(offlineConfig(true));
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);
        customerServiceRepairOrderService.chooseOffline(orderId, 88L);

        CustomerServiceOfflinePayInfoBO again = customerServiceRepairOrderService.chooseOffline(orderId, 88L);

        assertEquals("北京御旺宸发科技有限公司", again.getAccountName());
        assertEquals(CustomerServiceRepairOrderPayStatusEnum.WAITING_CONFIRM.getStatus(),
                customerServiceRepairOrderMapper.selectById(orderId).getPayStatus());
    }

    @Test
    public void testChooseOffline_disabledRejected() {
        when(tradeConfigService.getTradeConfig()).thenReturn(offlineConfig(false));
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.chooseOffline(orderId, 88L));
        assertEquals(CUSTOMER_SERVICE_OFFLINE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    public void testConfirmOffline_marksPaidAndIsIdempotent() {
        when(tradeConfigService.getTradeConfig()).thenReturn(offlineConfig(true));
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);
        customerServiceRepairOrderService.chooseOffline(orderId, 88L);

        customerServiceRepairOrderService.confirmOffline(orderId, 1L, "流水号", "付款人", LocalDateTime.now(), "到账");
        CustomerServiceRepairOrderDO paid = customerServiceRepairOrderMapper.selectById(orderId);
        assertEquals(CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus(), paid.getPayStatus());
        assertEquals(99002L, paid.getPayOrderId());
        assertEquals(1L, paid.getOfflineConfirmUserId());
        // 对公确认的流水号 / 付款人 / 到账时间必须留在流转日志里
        String payConfirmRemark = customerServiceLogMapper.selectListByTarget(3, orderId).stream()
                .filter(log -> "PAY_CONFIRM".equals(log.getAction()))
                .findFirst().orElseThrow().getRemark();
        assertTrue(payConfirmRemark.contains("流水号") && payConfirmRemark.contains("付款人"));
        assertTrue(payConfirmRemark.contains("到账时间"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.confirmOffline(orderId, 1L, null, null, null, null));
        assertEquals(CUSTOMER_SERVICE_OFFLINE_CONFIRM_FAIL.getCode(), ex.getCode());
    }

    @Test
    public void testMarkShipped_requiresPaid() {
        Long requestId = createRepair(88L);
        customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.markShipped(requestId, 88L, "顺丰", "SF123"));
        assertEquals(CUSTOMER_SERVICE_REPAIR_NOT_PAID.getCode(), ex.getCode());
    }

    @Test
    public void testMarkShipped_paidOrderMovesToShipped() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);
        customerServiceRepairOrderService.updateOrderPaid(orderId, 99001L);

        ServiceException blank = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.markShipped(requestId, 88L, "顺丰", " "));
        assertEquals(CUSTOMER_SERVICE_REPAIR_SHIP_NO_REQUIRED.getCode(), blank.getCode());

        customerServiceRepairOrderService.markShipped(requestId, 88L, "顺丰", "SF1234567890");
        CustomerServiceRepairRequestDO shipped = customerServiceRepairRequestMapper.selectById(requestId);
        assertEquals(CustomerServiceRepairStatusEnum.SHIPPED.getStatus(), shipped.getStatus());
        assertEquals("SF1234567890", shipped.getShipNo());
        assertEquals("顺丰", shipped.getShipCompany());
        assertNotNull(shipped.getShipTime());
    }

    @Test
    public void testMarkShipped_zeroPayAllowed() {
        Long requestId = createRepair(88L);
        customerServiceRepairOrderService.createOrder(requestId, 0, 0, "保内免费", 1L);

        customerServiceRepairOrderService.markShipped(requestId, 88L, "顺丰", "SF000");
        assertEquals(CustomerServiceRepairStatusEnum.SHIPPED.getStatus(),
                customerServiceRepairRequestMapper.selectById(requestId).getStatus());
    }

    @Test
    public void testComplete_recordsOperatorAndFinishesRequest() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 0, "维修费", 1L);
        customerServiceRepairOrderService.payOnline(orderId, 88L);
        customerServiceRepairOrderService.updateOrderPaid(orderId, 99001L);
        customerServiceRepairOrderService.markShipped(requestId, 88L, "顺丰", "SF001");

        customerServiceRepairOrderService.complete(orderId, 7L);

        CustomerServiceRepairOrderDO order = customerServiceRepairOrderMapper.selectById(orderId);
        assertNotNull(order.getCompleteTime());
        assertEquals(7L, order.getCompleteOperatorId());
        assertEquals(CustomerServiceRepairStatusEnum.COMPLETED.getStatus(),
                customerServiceRepairRequestMapper.selectById(requestId).getStatus());
    }

    @Test
    public void testComplete_unpaidRejected() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 0, "维修费", 1L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.complete(orderId, 7L));
        assertEquals(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_COMPLETABLE.getCode(), ex.getCode());
    }

    @Test
    public void testComplete_idempotent() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 0, 0, "保内免费", 1L);
        customerServiceRepairOrderService.markShipped(requestId, 88L, "顺丰", "SF002");

        customerServiceRepairOrderService.complete(orderId, 7L);
        customerServiceRepairOrderService.complete(orderId, 7L);

        assertEquals(7L, customerServiceRepairOrderMapper.selectById(orderId).getCompleteOperatorId());
    }

    @Test
    public void testRefund_onlineCreatesPayRefund() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);
        customerServiceRepairOrderService.payOnline(orderId, 88L);
        customerServiceRepairOrderService.updateOrderPaid(orderId, 99001L);

        customerServiceRepairOrderService.refund(orderId, 1L, "127.0.0.1", "客户取消维修");

        CustomerServiceRepairOrderDO refunding = customerServiceRepairOrderMapper.selectById(orderId);
        assertEquals(CustomerServiceRepairOrderRefundStatusEnum.REFUNDING.getStatus(), refunding.getRefundStatus());
        assertEquals(88001L, refunding.getPayRefundId());
        assertEquals(12000, refunding.getRefundPrice());
        assertEquals("客户取消维修", refunding.getRefundReason());
        ArgumentCaptor<PayRefundCreateReqDTO> captor = ArgumentCaptor.forClass(PayRefundCreateReqDTO.class);
        verify(payRefundApi).createRefund(captor.capture());
        assertEquals("repair:" + orderId, captor.getValue().getMerchantOrderId());
        assertEquals("repair-refund:" + orderId, captor.getValue().getMerchantRefundId());
        assertEquals(12000, captor.getValue().getPrice());
    }

    @Test
    public void testRefund_offlineMarksRefundedDirectly() {
        when(tradeConfigService.getTradeConfig()).thenReturn(offlineConfig(true));
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);
        customerServiceRepairOrderService.chooseOffline(orderId, 88L);
        customerServiceRepairOrderService.confirmOffline(orderId, 1L, null, null, null, "到账");

        customerServiceRepairOrderService.refund(orderId, 1L, "127.0.0.1", "线下退款");

        CustomerServiceRepairOrderDO refunded = customerServiceRepairOrderMapper.selectById(orderId);
        assertEquals(CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus(), refunded.getRefundStatus());
        assertNotNull(refunded.getRefundTime());
        assertEquals(12000, refunded.getRefundPrice());
        verify(payRefundApi, never()).createRefund(any());
    }

    @Test
    public void testRefund_unpaidRejected() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairOrderService.refund(orderId, 1L, "127.0.0.1", "退款"));
        assertEquals(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_REFUNDABLE.getCode(), ex.getCode());
    }

    @Test
    public void testUpdateRefunded_success() {
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);
        customerServiceRepairOrderService.payOnline(orderId, 88L);
        customerServiceRepairOrderService.updateOrderPaid(orderId, 99001L);
        customerServiceRepairOrderService.refund(orderId, 1L, "127.0.0.1", "退款");

        customerServiceRepairOrderService.updateRefunded("repair:" + orderId,
                "repair-refund:" + orderId, 88001L);

        CustomerServiceRepairOrderDO refunded = customerServiceRepairOrderMapper.selectById(orderId);
        assertEquals(CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus(), refunded.getRefundStatus());
        assertNotNull(refunded.getRefundTime());
    }

    @Test
    public void testCancel_afterRefundAllowed() {
        when(tradeConfigService.getTradeConfig()).thenReturn(offlineConfig(true));
        Long requestId = createRepair(88L);
        Long orderId = customerServiceRepairOrderService.createOrder(requestId, 10000, 2000, "维修费", 1L);
        customerServiceRepairOrderService.chooseOffline(orderId, 88L);
        customerServiceRepairOrderService.confirmOffline(orderId, 1L, null, null, null, "到账");
        customerServiceRepairOrderService.refund(orderId, 1L, "127.0.0.1", "线下退款");

        customerServiceRepairOrderService.cancel(requestId, "退款后取消", 1L);

        assertEquals(CustomerServiceRepairStatusEnum.CANCELED.getStatus(),
                customerServiceRepairRequestMapper.selectById(requestId).getStatus());
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderMapper.selectById(orderId);
        // 已退款订单保留支付/退款痕迹，不改成「已取消」
        assertEquals(CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus(), order.getPayStatus());
        assertEquals(CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus(), order.getRefundStatus());
    }

    @Test
    public void testGetPage_filtersByPayStatus() {
        Long paidRequest = createRepair(88L);
        Long paidOrder = customerServiceRepairOrderService.createOrder(paidRequest, 10000, 0, "维修费", 1L);
        customerServiceRepairOrderService.payOnline(paidOrder, 88L);
        customerServiceRepairOrderService.updateOrderPaid(paidOrder, 99001L);
        Long unpaidRequest = createRepair(99L);
        customerServiceRepairOrderService.createOrder(unpaidRequest, 5000, 0, "维修费", 1L);

        var page = customerServiceRepairOrderService.getPage(null, null,
                CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus(), null, null,
                new cn.iocoder.yudao.framework.common.pojo.PageParam());
        assertEquals(1, page.getTotal());
        assertEquals(paidOrder, page.getList().get(0).getId());
    }

}
