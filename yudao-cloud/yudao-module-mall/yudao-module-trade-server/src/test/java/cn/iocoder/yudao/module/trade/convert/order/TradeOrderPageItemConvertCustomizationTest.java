package cn.iocoder.yudao.module.trade.convert.order;

import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageItemRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 后台订单**列表**要能看出是不是定制单（014 / FR-375 / T061）
 *
 * 没有这个字段，前端就算想打「定制」标记也拿不到数据 —— 筛选能隔离，但混合列表里分不清。
 */
public class TradeOrderPageItemConvertCustomizationTest {

    private TradeOrderDO order(Boolean customization) {
        return TradeOrderDO.builder()
                .id(1001L).no("NO-1001").type(0).terminal(0).userId(88L).userIp("127.0.0.1")
                .status(TradeOrderStatusEnum.UNDELIVERED.getStatus()).productCount(1).payStatus(true)
                .discountPrice(0).deliveryPrice(0).adjustPrice(0).payPrice(45520)
                .deliveryType(1).couponPrice(0).pointPrice(0)
                .customization(customization)
                .build();
    }

    @Test
    public void testConvert_customizationOrder_carriesFlag() {
        List<TradeOrderItemDO> items = Collections.emptyList();

        TradeOrderPageItemRespVO vo = TradeOrderConvert.INSTANCE.convert(order(true), items);

        assertEquals(Boolean.TRUE, vo.getCustomization());
    }

    @Test
    public void testConvert_normalOrder_flagIsNotTrue() {
        List<TradeOrderItemDO> items = Collections.emptyList();

        TradeOrderPageItemRespVO vo = TradeOrderConvert.INSTANCE.convert(order(false), items);

        assertEquals(Boolean.FALSE, vo.getCustomization());
    }

}
