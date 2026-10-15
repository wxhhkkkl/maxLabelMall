package cn.iocoder.yudao.module.trade.dal.mysql.invoice;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeOrderInvoiceDO;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTypeEnum;
import cn.iocoder.yudao.module.trade.enums.invoice.TradeOrderInvoiceStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TradeOrderInvoiceMapper} 的冒烟测试（T377）。
 *
 * ⚠️ **本类是 `yudao-module-trade-server` 的第一个测试类** —— 在此之前这个模块
 * 连 `src/test/java` 目录都没有（只有 test resources）。所以它同时也是
 * **测试脚手架的验收**：H2 能不能建出 `trade_order_invoice` / `trade_invoice_title`，
 * MyBatis-Plus 能不能把 DO 映射上去。
 *
 * 它红的话，先去看 `src/test/resources/sql/create_tables.sql` 有没有补那两张表（T373），
 * 而不是怀疑业务代码。
 */
public class TradeOrderInvoiceMapperTest extends BaseDbUnitTest {

    @Resource
    private TradeOrderInvoiceMapper invoiceMapper;

    private TradeOrderInvoiceDO buildInvoice(Long orderId) {
        return TradeOrderInvoiceDO.builder()
                .orderId(orderId).orderNo("NO-" + orderId).userId(88L)
                .type(InvoiceTypeEnum.E_SPECIAL.getType())
                .status(TradeOrderInvoiceStatusEnum.WAITING.getStatus())
                .amount(7800)
                .titleName("南京路桥机械有限公司").taxNo("91320104660690975E")
                .titleAddress("南京市玄武区…").titlePhone("18913984596").bankName("工商银行南京分行 6222…")
                .build();
    }

    @Test
    public void testInsertAndSelect_success() {
        // 准备
        invoiceMapper.insert(buildInvoice(1001L));

        // 调用 + 断言：五个抬头字段原样存回（含 NULL 与非 NULL 两种）
        TradeOrderInvoiceDO invoice = invoiceMapper.selectByOrderId(1001L);
        assertNotNull(invoice);
        assertEquals(1001L, invoice.getOrderId());
        assertEquals("NO-1001", invoice.getOrderNo());
        assertEquals(7800, invoice.getAmount());
        assertEquals("南京路桥机械有限公司", invoice.getTitleName());
        assertEquals("91320104660690975E", invoice.getTaxNo());
        assertEquals(TradeOrderInvoiceStatusEnum.WAITING.getStatus(), invoice.getStatus());
        // 未开具的字段保持为空 —— 别让 H2 的默认值把它们填成 0 / 空串
        assertNull(invoice.getInvoiceFileUrl());
        assertNull(invoice.getIssueTime());
        assertNull(invoice.getIssueUserId());
    }

    /**
     * FR-160：一个订单最多一条申请。**这条由数据库的唯一索引兜底** ——
     * 服务层的"尚无申请"检查在并发下会双双通过，只有 `uk_order_id` 挡得住。
     */
    @Test
    public void testInsert_duplicateOrderId_fail() {
        invoiceMapper.insert(buildInvoice(1001L));
        assertThrows(DuplicateKeyException.class, () -> invoiceMapper.insert(buildInvoice(1001L)));
    }

    /**
     * 退款 / 取消 → 申请作废（Q30 + **2026-10-15 所有者口径：已开票的也要撤回**）
     *
     * 原来的规则是"已开票的不动（票在客户手里，系统不替财务撤回）"，
     * 所有者实测后改掉了：**退款了票就该撤回** —— 前台不再显示，后台也不能再更新。
     * 红冲本身仍由财务线下处理，但系统里这张票的**可用性**必须立刻消失。
     */
    @Test
    public void testUpdateStatusToCanceledByOrderId_waitingAndIssued() {
        // 待开票：作废
        invoiceMapper.insert(buildInvoice(1001L));
        assertEquals(1, invoiceMapper.updateStatusToCanceledByOrderId(1001L, LocalDateTime.now()));
        TradeOrderInvoiceDO canceled = invoiceMapper.selectByOrderId(1001L);
        assertEquals(TradeOrderInvoiceStatusEnum.CANCELED.getStatus(), canceled.getStatus());
        assertNotNull(canceled.getCancelTime());

        // **已开票的：同样作废**（票要撤回）—— 但文件地址保留，便于财务核对
        TradeOrderInvoiceDO issued = buildInvoice(1002L);
        issued.setStatus(TradeOrderInvoiceStatusEnum.ISSUED.getStatus());
        issued.setInvoiceFileUrl("https://img.yuwangchenfa.com/invoice/1002.pdf");
        invoiceMapper.insert(issued);
        assertEquals(1, invoiceMapper.updateStatusToCanceledByOrderId(1002L, LocalDateTime.now()));
        TradeOrderInvoiceDO revoked = invoiceMapper.selectByOrderId(1002L);
        assertEquals(TradeOrderInvoiceStatusEnum.CANCELED.getStatus(), revoked.getStatus());
        assertEquals("https://img.yuwangchenfa.com/invoice/1002.pdf", revoked.getInvoiceFileUrl());

        // 已经作废过的：幂等，影响行数 0（不是异常）—— handler 会被调用两次（按订单 + 按订单项）
        assertEquals(0, invoiceMapper.updateStatusToCanceledByOrderId(1002L, LocalDateTime.now()));

        // 本来就没申请：影响行数同样是 0
        assertEquals(0, invoiceMapper.updateStatusToCanceledByOrderId(9999L, LocalDateTime.now()));
    }

    /**
     * FR-178 的实现前提：订单列表要**一次查完一页**，不许逐笔查（N+1）。
     */
    @Test
    public void testSelectListByOrderIds_success() {
        invoiceMapper.insert(buildInvoice(1001L));
        invoiceMapper.insert(buildInvoice(1002L));
        invoiceMapper.insert(buildInvoice(1003L));

        assertEquals(2, invoiceMapper.selectListByOrderIds(java.util.List.of(1001L, 1002L, 9999L)).size());
    }

}
