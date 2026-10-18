package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.invoice.TradeOrderInvoiceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link TradeInvoiceOrderHandler} 的单元测试（T402）
 *
 * 它只有一个职责：订单取消时，把**订单编号**交给开票服务去作废。
 * 传错参数（比如把 `order.getId()` 写成别的）不会报错，只会静默地什么也不作废 ——
 * 所以这里断言的是**参数**，不是"方法被调过"。
 */
public class TradeInvoiceOrderHandlerTest extends BaseMockitoUnitTest {

    // ⚠️ 纯 Mockito 测试（`BaseMockitoUnitTest`）**没有 Spring 上下文**：`@Resource` 注入不到 bean，
    // 会得到 "this.handler is null"。这里必须是 Mockito 的 @InjectMocks / @Mock
    @InjectMocks
    private TradeInvoiceOrderHandler handler;

    @Mock
    private TradeOrderInvoiceService tradeOrderInvoiceService;

    @Test
    public void testAfterCancelOrder_cancelInvoiceByOrderId() {
        TradeOrderDO order = TradeOrderDO.builder().id(1001L).build();

        handler.afterCancelOrder(order, Collections.emptyList());

        // 参数是订单编号 —— 作废是按订单找申请的
        ArgumentCaptor<Long> orderIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(tradeOrderInvoiceService, times(1)).cancelInvoiceByOrderId(orderIdCaptor.capture());
        assertEquals(1001L, orderIdCaptor.getValue());
    }

    /**
     * **部分退款**也要撤回发票（2026-10-15 所有者口径）。
     *
     * 部分退款**不取消订单**（`afterCancelOrder` 不会触发），只会逐项走 `afterCancelOrderItem` ——
     * 所以这个钩子必须有，否则"退了一件商品，票还在前台挂着"。
     * 两次调用是幂等的（服务层保证），所以这里不担心重复。
     */
    @Test
    public void testAfterCancelOrderItem_cancelInvoiceByOrderId() {
        TradeOrderDO order = TradeOrderDO.builder().id(1001L).build();

        handler.afterCancelOrderItem(order, new TradeOrderItemDO().setId(11L));

        ArgumentCaptor<Long> orderIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(tradeOrderInvoiceService, times(1)).cancelInvoiceByOrderId(orderIdCaptor.capture());
        assertEquals(1001L, orderIdCaptor.getValue());
    }

}
