package cn.iocoder.yudao.module.trade.convert.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderPageItemRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 用户端订单列表的「再次购买」标识（2026-11-04 所有者拍板）
 *
 * 只有**普通商品单**给入口：定制单 / 企业授权单 / 个人会员订阅单都走各自流程，
 * 不能直接拿订单项的 SKU 去结算。判定必须在服务端做 —— 列表 VO 里没有这些原始字段。
 */
public class AppTradeOrderPageItemConvertReorderTest {

    private TradeOrderDO order(Boolean customization, Boolean subscription, Long maxlabelOrgId) {
        return TradeOrderDO.builder()
                .id(2001L).no("NO-2001").type(1).terminal(10).userId(88L).userIp("127.0.0.1")
                .status(TradeOrderStatusEnum.COMPLETED.getStatus()).productCount(1).payStatus(true)
                .discountPrice(0).deliveryPrice(0).adjustPrice(0).payPrice(10000).deliveryType(1)
                .couponPrice(0).pointPrice(0)
                .customization(customization)
                .maxlabelSubscription(subscription)
                .maxlabelOrgId(maxlabelOrgId)
                .build();
    }

    private Boolean canReorder(TradeOrderDO order) {
        PageResult<AppTradeOrderPageItemRespVO> page = TradeOrderConvert.INSTANCE.convertPage02(
                new PageResult<>(Collections.singletonList(order), 1L), Collections.emptyList());
        return page.getList().get(0).getCanReorder();
    }

    @Test
    public void testNormalOrder_canReorder() {
        assertEquals(Boolean.TRUE, canReorder(order(false, false, null)));
    }

    @Test
    public void testCustomizationOrder_cannotReorder() {
        assertEquals(Boolean.FALSE, canReorder(order(true, false, null)));
    }

    @Test
    public void testSubscriptionOrder_cannotReorder() {
        assertEquals(Boolean.FALSE, canReorder(order(false, true, null)));
    }

    @Test
    public void testEnterpriseOrder_cannotReorder() {
        assertEquals(Boolean.FALSE, canReorder(order(false, false, 3001L)));
    }

}
