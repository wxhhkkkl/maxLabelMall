package cn.iocoder.yudao.module.trade.convert.order;

import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderPageItemRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TradeOrderConvert} 物流字段的映射回归（010 / FR-273）。
 *
 * C 端列表靠 `logisticsNo` 决定要不要显示「查看物流」入口。MapStruct 是按名自动映射的，
 * 这个测试钉住「VO 加了字段、DO 也有、两边确实连上了」——少任何一边都会静默变 null。
 */
public class TradeOrderConvertTest {

    @Test
    public void testConvert02_logisticsNo_copied() {
        TradeOrderDO order = TradeOrderDO.builder()
                .id(1L).no("NO-1").logisticsNo("SF1234567890")
                .build();

        AppTradeOrderPageItemRespVO vo = TradeOrderConvert.INSTANCE.convert02(order, Collections.emptyList());

        assertEquals("SF1234567890", vo.getLogisticsNo());
    }

    @Test
    public void testConvert02_noLogisticsNo_staysNull() {
        TradeOrderDO order = TradeOrderDO.builder().id(1L).no("NO-1").build();

        AppTradeOrderPageItemRespVO vo = TradeOrderConvert.INSTANCE.convert02(order, Collections.emptyList());

        assertNull(vo.getLogisticsNo());
    }

}
