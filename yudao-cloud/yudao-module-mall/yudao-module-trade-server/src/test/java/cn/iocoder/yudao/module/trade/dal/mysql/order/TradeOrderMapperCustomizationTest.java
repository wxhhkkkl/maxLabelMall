package cn.iocoder.yudao.module.trade.dal.mysql.order;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 后台「只看定制单」筛选（014 / R93 / US49）
 *
 * ⚠️ 列表与统计必须共用同一个条件（008 的教训）；这里两处都验。
 */
public class TradeOrderMapperCustomizationTest extends BaseDbUnitTest {

    @Resource
    private TradeOrderMapper orderMapper;

    private void createOrder(boolean customization, int payPrice) {
        TradeOrderDO order = TradeOrderDO.builder()
                .no("NO-" + System.nanoTime()).type(0).terminal(0).userId(88L).userIp("127.0.0.1")
                .status(TradeOrderStatusEnum.UNDELIVERED.getStatus()).productCount(1).payStatus(true)
                .discountPrice(0).deliveryPrice(0).adjustPrice(0).payPrice(payPrice)
                .deliveryType(1).receiverName("张三").receiverMobile("13800008888")
                .receiverAreaId(110101).receiverDetailAddress("xx 路 1 号")
                .couponId(0L).couponPrice(0).pointPrice(0)
                .customization(customization)
                .build();
        orderMapper.insert(order);
    }

    @Test
    public void testSelectPage_filterCustomization() {
        createOrder(true, 1000);
        createOrder(true, 2000);
        createOrder(false, 3000);

        TradeOrderPageReqVO reqVO = new TradeOrderPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setCustomization(true);
        assertEquals(2, orderMapper.selectPage(reqVO, (java.util.Set<Long>) null).getTotal());
    }

    @Test
    public void testSummary_sameCondition() {
        createOrder(true, 1000);
        createOrder(false, 3000);

        TradeOrderPageReqVO reqVO = new TradeOrderPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setCustomization(true);
        List<Map<String, Object>> list = orderMapper.selectOrderSummaryGroupByRefundStatus(reqVO, null);
        assertEquals(1, list.size());
        assertEquals(1L, ((Number) list.get(0).get("count")).longValue());
    }

}
