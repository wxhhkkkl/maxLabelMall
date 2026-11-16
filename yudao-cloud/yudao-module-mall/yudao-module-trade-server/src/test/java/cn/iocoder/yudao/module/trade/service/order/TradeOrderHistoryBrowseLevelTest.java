package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 历史订单不受浏览等级变化影响（019 / FR-531）
 *
 * 回归护栏：订单明细读取路径**没有**、也不应该有浏览等级过滤。
 * 该测试保护「不改变」行为，允许一开始就是绿的。
 */
public class TradeOrderHistoryBrowseLevelTest extends BaseDbUnitTest {

    @Resource
    private TradeOrderItemMapper tradeOrderItemMapper;

    @Test
    void orderItemsRemainReadableAfterLevelDowngrade() {
        TradeOrderItemDO item = new TradeOrderItemDO()
                .setUserId(1001L).setOrderId(9001L)
                .setSpuId(3L).setSpuName("等级商品").setSkuId(4L)
                .setCount(1).setPrice(100).setDiscountPrice(0).setPayPrice(100)
                .setAfterSaleStatus(0);
        tradeOrderItemMapper.insert(item);

        List<TradeOrderItemDO> items = tradeOrderItemMapper.selectListByOrderId(9001L);

        assertEquals(1, items.size());
        assertEquals(3L, items.get(0).getSpuId());
        assertEquals("等级商品", items.get(0).getSpuName());
    }

}
