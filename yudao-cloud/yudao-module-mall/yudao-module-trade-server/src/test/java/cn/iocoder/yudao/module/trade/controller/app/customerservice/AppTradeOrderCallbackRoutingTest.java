package cn.iocoder.yudao.module.trade.controller.app.customerservice;

import cn.iocoder.yudao.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import cn.iocoder.yudao.module.trade.controller.app.order.AppTradeOrderController;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairOrderService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 支付回调前缀路由（021 / T038）
 *
 * 唯一的既有代码改动：`repair:` 走维修订单，纯数字仍走交易订单。
 */
@ExtendWith(MockitoExtension.class)
public class AppTradeOrderCallbackRoutingTest {

    @Mock
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Mock
    private CustomerServiceRepairOrderService customerServiceRepairOrderService;

    @InjectMocks
    private AppTradeOrderController controller;

    private PayOrderNotifyReqDTO notify(String merchantOrderId, Long payOrderId) {
        PayOrderNotifyReqDTO dto = new PayOrderNotifyReqDTO();
        dto.setMerchantOrderId(merchantOrderId);
        dto.setPayOrderId(payOrderId);
        return dto;
    }

    @Test
    public void testRepairPrefix_routesToRepairService() {
        controller.updateOrderPaid(notify("repair:701", 99001L));

        verify(customerServiceRepairOrderService).updateOrderPaid(701L, 99001L);
        verifyNoInteractions(tradeOrderUpdateService);
    }

    @Test
    public void testNumeric_routesToTradeOrderService() {
        controller.updateOrderPaid(notify("12345", 99002L));

        verify(tradeOrderUpdateService).updateOrderPaid(12345L, 99002L);
        verifyNoInteractions(customerServiceRepairOrderService);
    }

}
