package cn.iocoder.yudao.module.trade.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.offline.TradeOrderOfflinePaymentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.offline.TradeOrderOfflinePaymentMapper;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TradeOrderMapper} 的单元测试（008 / FR-209）—— **只测「支付渠道」这个筛选**
 *
 * 为什么单独建这个测试类：`TradeOrderUpdateServiceImpl` 依赖一大堆 API/Service（见同目录的
 * 说明），而这个筛选是**纯查询**，用 mapper 直测成本最低、也最贴近线上 SQL。
 *
 * ⚠️ 本用例要钉住的那条**最容易想当然**：筛选「对公转账」**不能**按
 * `trade_order.pay_channel_code` 来 —— 对公单在**确认收款前**那一列**是空的**
 * （它记的是"支付成功的渠道"），照它筛会把最该被财务看到的「待收款」单**全部漏掉**。
 */
@Import(TradeOrderMapperTest.TestConfiguration.class) // 只需要 mapper，本类不加任何 mock
public class TradeOrderMapperTest extends BaseDbUnitTest {

    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private TradeOrderOfflinePaymentMapper offlinePaymentMapper;

    /** 造一笔订单（`trade_order` 的 NOT NULL 列不少，漏一个就插不进去） */
    private TradeOrderDO createOrder(String payChannelCode, Integer status) {
        TradeOrderDO order = TradeOrderDO.builder()
                .no("NO-" + System.nanoTime()).type(0).terminal(0).userId(88L).userIp("127.0.0.1")
                .status(status).productCount(1).payStatus(!TradeOrderStatusEnum.isUnpaid(status))
                .discountPrice(0).deliveryPrice(0).adjustPrice(0).payPrice(10800).deliveryType(1)
                .payChannelCode(payChannelCode)
                .receiverName("张三").receiverMobile("13800008888").receiverAreaId(110101)
                .receiverDetailAddress("xx 路 1 号")
                .couponId(0L).couponPrice(0).pointPrice(0)
                .build();
        tradeOrderMapper.insert(order);
        return order;
    }

    private void markOffline(Long orderId) {
        offlinePaymentMapper.insert(TradeOrderOfflinePaymentDO.builder().orderId(orderId).build());
    }

    private List<Long> selectIdsByPayChannel(String payChannelCode, Integer status) {
        TradeOrderPageReqVO reqVO = new TradeOrderPageReqVO();
        reqVO.setPayChannelCode(payChannelCode);
        reqVO.setStatus(status);
        // ⚠️ 必须显式转型：`null` 同时匹配 `selectPage(PageParam, Wrapper)` 与
        //    本接口的 `selectPage(TradeOrderPageReqVO, Set<Long>)`（后者是我们要的那条）
        PageResult<TradeOrderDO> page = tradeOrderMapper.selectPage(reqVO, (java.util.Set<Long>) null);
        return page.getList().stream().map(TradeOrderDO::getId).toList();
    }

    @Test
    public void testSelectPage_payChannelCodeOffline_foundBySubTable() {
        // 对公**待收款**单：子表有行，而 `pay_channel_code` **还是空的**（确认收款前都这样）
        TradeOrderDO waiting = createOrder(null, TradeOrderStatusEnum.UNPAID.getStatus());
        markOffline(waiting.getId());
        // 普通在线支付单
        TradeOrderDO online = createOrder(PayChannelEnum.WX_LITE.getCode(),
                TradeOrderStatusEnum.UNDELIVERED.getStatus());

        List<Long> ids = selectIdsByPayChannel(PayChannelEnum.OFFLINE.getCode(), null);

        // ⚠️ 这一条就是本用例的全部意义：按 `pay_channel_code` 筛的话**一条都筛不出来**
        assertEquals(List.of(waiting.getId()), ids);
        assertFalse(ids.contains(online.getId()));
    }

    @Test
    public void testSelectPage_payChannelCodeOffline_confirmedOrderAlsoMatched() {
        // 已确认收款的单（那一列这时才被写成 offline）**照样在结果里** ——
        // 这个筛选筛的是"**是不是对公单**"，不是"是不是待收款"；
        // 财务要只看待收款的，就再叠加 status=0（契约 §3：`payChannelCode=offline&status=0`）
        TradeOrderDO confirmed = createOrder(PayChannelEnum.OFFLINE.getCode(),
                TradeOrderStatusEnum.UNDELIVERED.getStatus());
        markOffline(confirmed.getId());

        List<Long> all = selectIdsByPayChannel(PayChannelEnum.OFFLINE.getCode(), null);
        List<Long> waitingOnly = selectIdsByPayChannel(PayChannelEnum.OFFLINE.getCode(),
                TradeOrderStatusEnum.UNPAID.getStatus());

        assertTrue(all.contains(confirmed.getId()));
        assertFalse(waitingOnly.contains(confirmed.getId()));
    }

    @Test
    public void testSelectPage_payChannelCodeOther_stillByColumn() {
        // 其余渠道**行为不变**（FR-211）：照旧按 `pay_channel_code` 这一列筛
        createOrder(null, TradeOrderStatusEnum.UNPAID.getStatus());
        TradeOrderDO online = createOrder(PayChannelEnum.WX_LITE.getCode(),
                TradeOrderStatusEnum.UNDELIVERED.getStatus());

        assertEquals(List.of(online.getId()), selectIdsByPayChannel(PayChannelEnum.WX_LITE.getCode(), null));
    }

    @Test
    public void testSelectPage_payChannelCodeEmpty_noFilter() {
        // 不传渠道 = 不筛（后台默认视图）—— 空串也当作没传（与 eqIfPresent 同一口径）
        TradeOrderDO a = createOrder(null, TradeOrderStatusEnum.UNPAID.getStatus());
        TradeOrderDO b = createOrder(PayChannelEnum.WX_LITE.getCode(),
                TradeOrderStatusEnum.UNDELIVERED.getStatus());

        assertEquals(2, selectIdsByPayChannel(null, null).size());
        assertEquals(2, selectIdsByPayChannel("", null).size());
    }

    /**
     * `BaseDbUnitTest` 需要一个 `@Import` 的目标；本类只用 mapper（框架已自动扫描），
     * 所以给一个空的配置类即可。
     */
    @org.springframework.boot.test.context.TestConfiguration
    public static class TestConfiguration {
    }

}
