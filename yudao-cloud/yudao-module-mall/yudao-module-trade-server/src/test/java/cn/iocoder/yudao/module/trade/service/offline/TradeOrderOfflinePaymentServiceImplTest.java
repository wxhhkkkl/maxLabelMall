package cn.iocoder.yudao.module.trade.service.offline;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.offline.TradeOrderOfflinePaymentDO;
import cn.iocoder.yudao.module.trade.dal.mysql.offline.TradeOrderOfflinePaymentMapper;
import cn.iocoder.yudao.module.trade.service.offline.bo.TradeOrderOfflineConfirmReqBO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TradeOrderOfflinePaymentService} 的单元测试（008 / US26）
 *
 * 要钉住的三条：
 * · 下单时**只插一行**、**不带任何确认信息**（流水号/确认人是财务确认收款时才填的）
 * · **重复调用不插第二条**（幂等 —— 数据库的 `uk_order_id` 是兜底，不该靠它报错）
 * · 「待收款」的判据 = **是对公单 且 `confirmTime` 为空**
 *
 * ⚠️ **每个用例用各自的订单号，断言也按订单号计数**：这个 H2 库在用例之间
 * **不会自动清空**（本模块既有的测试类也一样），共用同一个 orderId 会互相看到对方写的数据 ——
 * 2026-10-16 就是这么第一次跑红的（`testCreateOfflinePayment_success` 读到了另一个用例写的流水号）。
 */
@Import(TradeOrderOfflinePaymentServiceImpl.class)
public class TradeOrderOfflinePaymentServiceImplTest extends BaseDbUnitTest {

    @Resource
    private TradeOrderOfflinePaymentService offlinePaymentService;
    @Resource
    private TradeOrderOfflinePaymentMapper offlinePaymentMapper;

    // ==================== 下单时记录"这一单走对公转账" ====================

    @Test
    public void testCreateOfflinePayment_success() {
        offlinePaymentService.createOfflinePayment(1101L);

        TradeOrderOfflinePaymentDO offlinePayment = offlinePaymentService.getOfflinePaymentByOrderId(1101L);
        assertNotNull(offlinePayment);
        assertEquals(1101L, offlinePayment.getOrderId());
        // ⚠️ 下单那一刻**没有任何确认信息** —— 那些是财务确认收款时才填的
        assertNull(offlinePayment.getBankSerialNo());
        assertNull(offlinePayment.getPayerName());
        assertNull(offlinePayment.getPaidTime());
        assertNull(offlinePayment.getConfirmUserId());
        assertNull(offlinePayment.getConfirmTime());
    }

    @Test
    public void testCreateOfflinePayment_idempotent() {
        offlinePaymentService.createOfflinePayment(1102L);
        offlinePaymentService.createOfflinePayment(1102L); // 重复调用

        // **只该有一行** —— 幂等由服务层保证，不是靠数据库唯一键报错。
        // ⚠️ 按 orderId 计数，不数整张表：库在用例之间不清空
        assertEquals(1, offlinePaymentMapper.selectListByOrderIds(List.of(1102L)).size());
    }

    @Test
    public void testGetOfflinePaymentByOrderId_notOfflineOrder_returnNull() {
        // 不是对公单 → 返回 null（前端据此不渲染「待收款」区块）
        assertNull(offlinePaymentService.getOfflinePaymentByOrderId(1199L));
    }

    // ==================== 「待收款」的判据 ====================

    @Test
    public void testIsWaitingReceipt_notConfirmed_true() {
        offlinePaymentService.createOfflinePayment(1103L);

        assertTrue(offlinePaymentService.isWaitingReceipt(1103L));
    }

    @Test
    public void testIsWaitingReceipt_confirmed_false() {
        offlinePaymentService.createOfflinePayment(1104L);
        // 模拟财务确认收款：填上确认人与确认时间
        offlinePaymentMapper.updateById(new TradeOrderOfflinePaymentDO().setId(idOf(1104L))
                .setBankSerialNo("2026101600012345").setConfirmUserId(249L).setConfirmTime(LocalDateTime.now()));

        assertFalse(offlinePaymentService.isWaitingReceipt(1104L), "已确认收款的单不再是「待收款」");
    }

    @Test
    public void testIsWaitingReceipt_notOfflineOrder_false() {
        // 普通订单（不是对公单）当然不是「待收款」
        assertFalse(offlinePaymentService.isWaitingReceipt(1198L));
    }

    // ==================== 批量（订单列表按它判断"是不是对公单"） ====================

    @Test
    public void testGetOfflinePaymentListByOrderIds() {
        offlinePaymentService.createOfflinePayment(1105L);
        offlinePaymentService.createOfflinePayment(1106L);

        List<TradeOrderOfflinePaymentDO> list = offlinePaymentService.getOfflinePaymentListByOrderIds(
                List.of(1105L, 1106L, 1199L));

        // 只返回真实存在的两条；1199 不是对公单，自然不在结果里
        assertEquals(2, list.size());
        // 空集合要短路，不能拼出 `IN ()`（这个仓库在迁移脚本上踩过同一个坑）
        assertTrue(offlinePaymentService.getOfflinePaymentListByOrderIds(List.of()).isEmpty());
    }

    /**
     * 批量取「待收款」的订单号 —— C 端订单列表按它决定显示「待收款」还是「待支付」（FR-200）
     *
     * ⚠️ 与 `isWaitingReceipt` **同一口径**：只判自己这张表，"订单是否还付着"由调用方判。
     */
    @Test
    public void testGetWaitingReceiptOrderIds() {
        offlinePaymentService.createOfflinePayment(1107L); // 未确认 → 在结果里
        offlinePaymentService.createOfflinePayment(1108L); // 已确认 → 不在结果里
        offlinePaymentMapper.updateById(new TradeOrderOfflinePaymentDO().setId(idOf(1108L))
                .setBankSerialNo("2026101600099999").setConfirmUserId(249L).setConfirmTime(LocalDateTime.now()));

        Set<Long> waitingOrderIds = offlinePaymentService.getWaitingReceiptOrderIds(
                List.of(1107L, 1108L, 1197L)); // 1197 不是对公单

        assertEquals(Set.of(1107L), waitingOrderIds);
        // 空集合要短路，不能拼出 `IN ()`（与 getOfflinePaymentListByOrderIds 同一个坑）
        assertTrue(offlinePaymentService.getWaitingReceiptOrderIds(List.of()).isEmpty());
    }

    // ==================== 确认收款：回填痕迹 + **幂等闸门**（FR-204 / FR-205 / FR-206）====================

    private TradeOrderOfflineConfirmReqBO confirmBO(Long orderId, String bankSerialNo, Long confirmUserId) {
        return TradeOrderOfflineConfirmReqBO.builder()
                .orderId(orderId).bankSerialNo(bankSerialNo).payerName("南京路桥机械有限公司")
                .paidTime(LocalDateTime.of(2026, 10, 16, 10, 0)).remark("分两笔打的，这是第一笔")
                .confirmUserId(confirmUserId).build();
    }

    @Test
    public void testConfirmReceipt_success() {
        offlinePaymentService.createOfflinePayment(1201L);

        assertTrue(offlinePaymentService.confirmReceipt(confirmBO(1201L, "2026101600012345", 249L)));

        TradeOrderOfflinePaymentDO offlinePayment = offlinePaymentService.getOfflinePaymentByOrderId(1201L);
        assertEquals("2026101600012345", offlinePayment.getBankSerialNo());
        assertEquals("南京路桥机械有限公司", offlinePayment.getPayerName());
        assertEquals(LocalDateTime.of(2026, 10, 16, 10, 0), offlinePayment.getPaidTime());
        assertEquals("分两笔打的，这是第一笔", offlinePayment.getRemark());
        // ⚠️ 财务动作必须可追溯（FR-205）：谁确认的、什么时候
        assertEquals(249L, offlinePayment.getConfirmUserId());
        assertNotNull(offlinePayment.getConfirmTime());
        // 确认之后它就不再是「待收款」了
        assertFalse(offlinePaymentService.isWaitingReceipt(1201L));
    }

    @Test
    public void testConfirmReceipt_secondTime_false() {
        offlinePaymentService.createOfflinePayment(1202L);
        assertTrue(offlinePaymentService.confirmReceipt(confirmBO(1202L, "第一笔的流水号", 249L)));

        // 第二次（含并发）**改不到任何一行** —— `confirm_time IS NULL` 已不成立（FR-206 / R44）
        assertFalse(offlinePaymentService.confirmReceipt(confirmBO(1202L, "第二笔的流水号", 250L)));

        // 而且**不会拿第二次的流水号把第一次的痕迹盖掉** —— 幂等不只是"不报错"
        TradeOrderOfflinePaymentDO offlinePayment = offlinePaymentService.getOfflinePaymentByOrderId(1202L);
        assertEquals("第一笔的流水号", offlinePayment.getBankSerialNo());
        assertEquals(249L, offlinePayment.getConfirmUserId());
    }

    @Test
    public void testConfirmReceipt_notOfflineOrder_false() {
        // 普通订单没有这一行 → 改不到，返回 false（调用方据此报"不是对公转账订单"）
        assertFalse(offlinePaymentService.confirmReceipt(confirmBO(1196L, "2026101600012345", 249L)));
    }

    private Long idOf(Long orderId) {
        return offlinePaymentService.getOfflinePaymentByOrderId(orderId).getId();
    }

}
