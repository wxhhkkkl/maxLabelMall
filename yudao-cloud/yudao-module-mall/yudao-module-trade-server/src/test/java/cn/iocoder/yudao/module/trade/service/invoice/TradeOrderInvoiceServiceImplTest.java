package cn.iocoder.yudao.module.trade.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.controller.admin.invoice.vo.TradeOrderInvoicePageReqVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppTradeOrderInvoiceCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeOrderInvoiceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.invoice.TradeOrderInvoiceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTypeEnum;
import cn.iocoder.yudao.module.trade.enums.invoice.TradeOrderInvoiceStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TradeOrderInvoiceService} 的单元测试 —— **下单时创建申请**（US19 / T379）。
 *
 * 三条最要紧的断言：
 * · **一单一条**（FR-160）：第二次创建必须被拒（服务层先挡，库里的唯一索引兜底）
 * · **金额取订单实付**（FR-161）：请求体里**根本没有金额字段**，服务端从订单读 —— 这条用"落库值 == 订单 payPrice"来钉
 * · **实付 0 元不产生申请、且不报错**（spec 的 Edge Case）：订单照下，只是没票可开
 *
 * ⚠️ 不传 `invoice`（不开票）时**不产生任何记录**（FR-159）—— 这是"默认行为与今天一致"的证据。
 */
@Import(TradeOrderInvoiceServiceImpl.class)
public class TradeOrderInvoiceServiceImplTest extends BaseDbUnitTest {

    @Resource
    private TradeOrderInvoiceService invoiceService;
    @Resource
    private TradeOrderInvoiceMapper invoiceMapper;
    @Resource
    private TradeOrderMapper orderMapper;

    /** 造一笔**已支付**的订单（`trade_order` 在 H2 里的 NOT NULL 列不少，漏一个就插不进去） */
    private TradeOrderDO createOrder(int payPrice) {
        return createOrder(payPrice, true, TradeOrderStatusEnum.UNDELIVERED.getStatus(), 88L);
    }

    private TradeOrderDO createOrder(int payPrice, boolean payStatus, Integer status, Long userId) {
        TradeOrderDO order = TradeOrderDO.builder()
                .no("NO-" + System.nanoTime()).type(0).terminal(0).userId(userId).userIp("127.0.0.1")
                .status(status).productCount(1).payStatus(payStatus)
                .discountPrice(0).deliveryPrice(0).adjustPrice(0).payPrice(payPrice).deliveryType(1)
                .receiverName("张三").receiverMobile("13800008888").receiverAreaId(110101)
                .receiverDetailAddress("xx 路 1 号")
                .couponId(0L).couponPrice(0).pointPrice(0)
                .build();
        orderMapper.insert(order);
        return order;
    }

    private AppInvoiceApplyBaseVO normalInvoice() {
        AppInvoiceApplyBaseVO invoice = new AppInvoiceApplyBaseVO();
        invoice.setType(InvoiceTypeEnum.E_NORMAL.getType());
        invoice.setTitleName("南京路桥机械有限公司");
        invoice.setTaxNo("91320104660690975E");
        return invoice;
    }

    // ==================== 下单时申请 ====================

    @Test
    public void testCreateInvoice_success() {
        // 准备
        TradeOrderDO order = createOrder(7800);

        // 调用
        Long id = invoiceService.createInvoice(order, normalInvoice());

        // 断言：一条申请，字段与提交的一致，金额取**订单实付**，状态是待开票
        TradeOrderInvoiceDO invoice = invoiceMapper.selectById(id);
        assertNotNull(invoice);
        assertEquals(order.getId(), invoice.getOrderId());
        assertEquals(order.getNo(), invoice.getOrderNo(), "订单号是下单时抄一份的");
        assertEquals(88L, invoice.getUserId());
        assertEquals(InvoiceTypeEnum.E_NORMAL.getType(), invoice.getType());
        assertEquals(TradeOrderInvoiceStatusEnum.WAITING.getStatus(), invoice.getStatus());
        assertEquals(7800, invoice.getAmount(), "开票金额必须取订单实付（请求里没有金额字段）");
        assertEquals("南京路桥机械有限公司", invoice.getTitleName());
        // 未开具
        assertNull(invoice.getInvoiceFileUrl());
        assertNull(invoice.getIssueTime());
    }

    @Test
    public void testCreateInvoice_amountIsTrimmedSnapshot() {
        // 名称前后的空格要 trim 掉（InvoiceValidator 负责），别把带空格的名字开进票里
        TradeOrderDO order = createOrder(100);
        AppInvoiceApplyBaseVO invoiceReq = normalInvoice();
        invoiceReq.setTitleName("  南京路桥机械有限公司  ");

        Long id = invoiceService.createInvoice(order, invoiceReq);

        assertEquals("南京路桥机械有限公司", invoiceMapper.selectById(id).getTitleName());
    }

    @Test
    public void testCreateInvoice_blankOptionalFieldsStoredAsNull() {
        // 前端提交普票时会把用不上的那几项传成空串（契约里就是这么写的）——
        // 落库必须归一成 NULL，否则"没填"在数据里就有两种表示（'' 与 NULL）
        TradeOrderDO order = createOrder(100);
        AppInvoiceApplyBaseVO invoiceReq = normalInvoice();
        invoiceReq.setTitleAddress("");
        invoiceReq.setTitlePhone("   ");
        invoiceReq.setBankName(null);

        Long id = invoiceService.createInvoice(order, invoiceReq);

        TradeOrderInvoiceDO invoice = invoiceMapper.selectById(id);
        assertNull(invoice.getTitleAddress());
        assertNull(invoice.getTitlePhone());
        assertNull(invoice.getBankName());
    }

    @Test
    public void testCreateInvoice_noInvoice_noRecord() {
        // 不开票（不传 invoice）—— 什么记录都不产生，也不报错（FR-159）
        TradeOrderDO order = createOrder(7800);

        assertNull(invoiceService.createInvoice(order, null));
        assertNull(invoiceMapper.selectByOrderId(order.getId()));
    }

    @Test
    public void testCreateInvoice_amountZero_ignoredWithoutException() {
        // 全额券抵扣、实付 0 元：**忽略**而不是拒绝 —— 不因为一个本来就开不了票的选择让客户下不了单
        TradeOrderDO order = createOrder(0);

        assertNull(invoiceService.createInvoice(order, normalInvoice()));
        assertNull(invoiceMapper.selectByOrderId(order.getId()));
    }

    @Test
    public void testCreateInvoice_duplicate_fail() {
        TradeOrderDO order = createOrder(7800);
        invoiceService.createInvoice(order, normalInvoice());

        // 再申请一次必须被拒（FR-160）
        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.createInvoice(order, normalInvoice()));
        assertEquals(INVOICE_ORDER_EXISTS.getCode(), e.getCode());
        // 且没有产生第二条
        assertEquals(1, invoiceMapper.selectListByOrderIds(List.of(order.getId())).size());
    }

    // ==================== 订单详情**补开**（US20 / T392） ====================

    /**
     * 补开请求 —— 七个校验逐条一个用例。
     *
     * ⚠️ 其中「不属于当前会员」这条**必须报"订单不存在"而不是"无权访问"**：
     * 后者会泄露"这个订单号是存在的"（枚举订单号就成了攻击面）。
     */
    private AppTradeOrderInvoiceCreateReqVO reopenReq(Long orderId) {
        AppTradeOrderInvoiceCreateReqVO reqVO = new AppTradeOrderInvoiceCreateReqVO();
        reqVO.setOrderId(orderId);
        reqVO.setType(InvoiceTypeEnum.E_NORMAL.getType());
        reqVO.setTitleName("南京路桥机械有限公司");
        reqVO.setTaxNo("91320104660690975E");
        return reqVO;
    }

    @Test
    public void testCreateInvoiceByMember_success() {
        TradeOrderDO order = createOrder(7800);

        Long id = invoiceService.createInvoiceByMember(88L, reopenReq(order.getId()));

        TradeOrderInvoiceDO invoice = invoiceMapper.selectById(id);
        assertNotNull(invoice);
        assertEquals(order.getId(), invoice.getOrderId());
        assertEquals(order.getNo(), invoice.getOrderNo());
        assertEquals(7800, invoice.getAmount());
        assertEquals(TradeOrderInvoiceStatusEnum.WAITING.getStatus(), invoice.getStatus());
    }

    @Test
    public void testCreateInvoiceByMember_orderNotFound_fail() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.createInvoiceByMember(88L, reopenReq(9999L)));
        assertEquals(ORDER_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    public void testCreateInvoiceByMember_notOwnOrder_fail() {
        TradeOrderDO others = createOrder(7800, true, TradeOrderStatusEnum.UNDELIVERED.getStatus(), 99L);

        // 报"订单不存在"—— 不能让人靠报错文案枚举出别人有哪些订单
        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.createInvoiceByMember(88L, reopenReq(others.getId())));
        assertEquals(ORDER_NOT_FOUND.getCode(), e.getCode());
        assertNull(invoiceMapper.selectByOrderId(others.getId()));
    }

    @Test
    public void testCreateInvoiceByMember_notPaid_fail() {
        TradeOrderDO order = createOrder(7800, false, TradeOrderStatusEnum.UNPAID.getStatus(), 88L);

        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.createInvoiceByMember(88L, reopenReq(order.getId())));
        assertEquals(INVOICE_ORDER_NOT_PAID.getCode(), e.getCode());
    }

    @Test
    public void testCreateInvoiceByMember_canceled_fail() {
        // 已支付但后来被取消（全额退款）—— 票不该再开
        TradeOrderDO order = createOrder(7800, true, TradeOrderStatusEnum.CANCELED.getStatus(), 88L);

        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.createInvoiceByMember(88L, reopenReq(order.getId())));
        assertEquals(INVOICE_ORDER_CANCELED.getCode(), e.getCode());
    }

    @Test
    public void testCreateInvoiceByMember_amountZero_fail() {
        // ⚠️ 与下单路径**刻意不同**：下单时是"忽略"（订单照下），补开时是"拒绝"（订单已存在，给个明确理由就好）
        TradeOrderDO order = createOrder(0);

        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.createInvoiceByMember(88L, reopenReq(order.getId())));
        assertEquals(INVOICE_ORDER_AMOUNT_ZERO.getCode(), e.getCode());
    }

    @Test
    public void testCreateInvoiceByMember_duplicate_fail() {
        TradeOrderDO order = createOrder(7800);
        invoiceService.createInvoice(order, normalInvoice());

        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.createInvoiceByMember(88L, reopenReq(order.getId())));
        assertEquals(INVOICE_ORDER_EXISTS.getCode(), e.getCode());
    }

    @Test
    public void testCreateInvoiceByMember_missingFields_fail() {
        // 抬头校验与下单**同一套规则**（InvoiceValidator），没有更宽松的旁路
        TradeOrderDO order = createOrder(7800);
        AppTradeOrderInvoiceCreateReqVO reqVO = reopenReq(order.getId());
        reqVO.setTaxNo(null); // 缺**税号** —— 2026-10-15 起两种票都必填（地址/电话/开户行改为选填）

        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.createInvoiceByMember(88L, reqVO));
        assertEquals(INVOICE_TITLE_FIELD_REQUIRED.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("纳税人识别号"), "实际：" + e.getMessage());
    }

    // ==================== 后台开具（US21 / T398） ====================

    /** 造一条**待开票**的申请（已支付订单 + 下单时申请） */
    private TradeOrderInvoiceDO prepareWaitingInvoice(int payPrice) {
        TradeOrderDO order = createOrder(payPrice);
        Long id = invoiceService.createInvoice(order, normalInvoice());
        return invoiceMapper.selectById(id);
    }

    @Test
    public void testIssueInvoice_success() {
        TradeOrderInvoiceDO invoice = prepareWaitingInvoice(7800);

        invoiceService.issueInvoice(invoice.getId(),
                "https://img.yuwangchenfa.com/invoice/" + invoice.getId() + ".pdf", 100L);

        TradeOrderInvoiceDO issued = invoiceMapper.selectById(invoice.getId());
        assertEquals(TradeOrderInvoiceStatusEnum.ISSUED.getStatus(), issued.getStatus());
        assertEquals("https://img.yuwangchenfa.com/invoice/" + invoice.getId() + ".pdf", issued.getInvoiceFileUrl());
        assertNotNull(issued.getIssueTime(), "要记开票时间（FR-170）");
        assertEquals(100L, issued.getIssueUserId(), "要记谁开的票（FR-170）");
    }

    @Test
    public void testIssueInvoice_reissue_overwritesWithoutHistory() {
        // Q31：传错了重传 —— **覆盖**，只留最新一份
        TradeOrderInvoiceDO invoice = prepareWaitingInvoice(7800);
        invoiceService.issueInvoice(invoice.getId(), "https://img.yuwangchenfa.com/invoice/wrong.pdf", 100L);

        invoiceService.issueInvoice(invoice.getId(), "https://img.yuwangchenfa.com/invoice/right.pdf", 101L);

        TradeOrderInvoiceDO issued = invoiceMapper.selectById(invoice.getId());
        assertEquals("https://img.yuwangchenfa.com/invoice/right.pdf", issued.getInvoiceFileUrl());
        assertEquals(101L, issued.getIssueUserId());
    }

    @Test
    public void testIssueInvoice_fileTypeNotAllowed_fail() {
        TradeOrderInvoiceDO invoice = prepareWaitingInvoice(7800);

        // 白名单外的扩展名：拒，且**状态与文件都不变**（FR-171）
        for (String url : new String[]{"https://x.com/a.txt", "https://x.com/a.exe", "https://x.com/a"}) {
            ServiceException e = assertThrows(ServiceException.class,
                    () -> invoiceService.issueInvoice(invoice.getId(), url, 100L), "应当拒绝：" + url);
            assertEquals(INVOICE_FILE_TYPE_NOT_ALLOWED.getCode(), e.getCode());
        }
        TradeOrderInvoiceDO still = invoiceMapper.selectById(invoice.getId());
        assertEquals(TradeOrderInvoiceStatusEnum.WAITING.getStatus(), still.getStatus());
        assertNull(still.getInvoiceFileUrl());
    }

    @Test
    public void testIssueInvoice_fileTypeCaseInsensitive() {
        // 大写扩展名（客户从微信存下来的文件常是 .PDF）不该被误拒
        TradeOrderInvoiceDO invoice = prepareWaitingInvoice(7800);
        invoiceService.issueInvoice(invoice.getId(), "https://x.com/a.PDF", 100L);
        assertEquals(TradeOrderInvoiceStatusEnum.ISSUED.getStatus(),
                invoiceMapper.selectById(invoice.getId()).getStatus());
    }

    @Test
    public void testIssueInvoice_notPaid_fail() {
        // 未支付订单的申请不该出现在待开票列表里；万一直连接口，也必须拒（FR-169）
        TradeOrderDO order = createOrder(7800, false, TradeOrderStatusEnum.UNPAID.getStatus(), 88L);
        TradeOrderInvoiceDO invoice = TradeOrderInvoiceDO.builder()
                .orderId(order.getId()).orderNo(order.getNo()).userId(88L)
                .type(InvoiceTypeEnum.E_NORMAL.getType())
                .status(TradeOrderInvoiceStatusEnum.WAITING.getStatus()).amount(7800)
                .titleName("南京路桥机械有限公司").build();
        invoiceMapper.insert(invoice);

        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.issueInvoice(invoice.getId(), "https://x.com/a.pdf", 100L));
        assertEquals(INVOICE_ISSUE_FAIL_ORDER_NOT_PAID.getCode(), e.getCode());
    }

    @Test
    public void testIssueInvoice_canceled_fail() {
        TradeOrderInvoiceDO invoice = prepareWaitingInvoice(7800);
        invoiceService.cancelInvoiceByOrderId(invoice.getOrderId());

        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.issueInvoice(invoice.getId(), "https://x.com/a.pdf", 100L));
        assertEquals(INVOICE_ISSUE_FAIL_STATUS_CANCELED.getCode(), e.getCode());
    }

    @Test
    public void testIssueInvoice_notFound_fail() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.issueInvoice(9999L, "https://x.com/a.pdf", 100L));
        assertEquals(INVOICE_NOT_FOUND.getCode(), e.getCode());
    }

    // ==================== 订单取消 → 作废（Q30 / FR-184） ====================

    @Test
    public void testCancelInvoiceByOrderId_waitingAndIssued() {
        // 待开票 → 作废
        TradeOrderInvoiceDO waiting = prepareWaitingInvoice(7800);
        invoiceService.cancelInvoiceByOrderId(waiting.getOrderId());
        TradeOrderInvoiceDO canceled = invoiceMapper.selectById(waiting.getId());
        assertEquals(TradeOrderInvoiceStatusEnum.CANCELED.getStatus(), canceled.getStatus());
        assertNotNull(canceled.getCancelTime());

        // **已开票的：退款后同样作废**（2026-10-15 所有者口径：票要撤回，前台不显示、后台不能再更新）
        TradeOrderDO otherOrder = createOrder(7800);
        TradeOrderInvoiceDO issued = invoiceMapper.selectById(
                invoiceService.createInvoice(otherOrder, normalInvoice()));
        invoiceService.issueInvoice(issued.getId(), "https://x.com/a.pdf", 100L);
        invoiceService.cancelInvoiceByOrderId(otherOrder.getId());
        TradeOrderInvoiceDO revoked = invoiceMapper.selectById(issued.getId());
        assertEquals(TradeOrderInvoiceStatusEnum.CANCELED.getStatus(), revoked.getStatus());
        // 文件地址保留（财务要能核对），但接口**不许再对它开具**
        assertEquals("https://x.com/a.pdf", revoked.getInvoiceFileUrl());
        ServiceException e = assertThrows(ServiceException.class,
                () -> invoiceService.issueInvoice(issued.getId(), "https://x.com/b.pdf", 100L));
        assertEquals(INVOICE_ISSUE_FAIL_STATUS_CANCELED.getCode(), e.getCode());
    }

    /**
     * 幂等：取消路径上 handler 会被**调用两次**（`afterCancelOrderItem` 逐项 + `afterCancelOrder`），
     * 两次都作废必须是安全的（第二次影响 0 行）
     */
    @Test
    public void testCancelInvoiceByOrderId_idempotent() {
        TradeOrderInvoiceDO waiting = prepareWaitingInvoice(7800);

        invoiceService.cancelInvoiceByOrderId(waiting.getOrderId());
        invoiceService.cancelInvoiceByOrderId(waiting.getOrderId());

        assertEquals(TradeOrderInvoiceStatusEnum.CANCELED.getStatus(),
                invoiceMapper.selectById(waiting.getId()).getStatus());
    }

    // ==================== 后台分页：只含已支付订单（FR-169 / T399） ====================

    /** 造一条**未支付**订单的申请（绕过下单路径直接插，模拟"下单时申请的、后来一直没付款"） */
    private TradeOrderInvoiceDO prepareUnpaidInvoice() {
        TradeOrderDO order = createOrder(7800, false, TradeOrderStatusEnum.UNPAID.getStatus(), 88L);
        TradeOrderInvoiceDO invoice = TradeOrderInvoiceDO.builder()
                .orderId(order.getId()).orderNo(order.getNo()).userId(88L)
                .type(InvoiceTypeEnum.E_NORMAL.getType())
                .status(TradeOrderInvoiceStatusEnum.WAITING.getStatus()).amount(7800)
                .titleName("南京路桥机械有限公司").build();
        invoiceMapper.insert(invoice);
        return invoice;
    }

    @Test
    public void testGetInvoicePage_waitingOnlyPaidOrders() {
        // 一笔已支付、一笔未支付，都是待开票
        TradeOrderInvoiceDO paid = prepareWaitingInvoice(7800);
        prepareUnpaidInvoice();

        TradeOrderInvoicePageReqVO reqVO = new TradeOrderInvoicePageReqVO();
        reqVO.setStatus(TradeOrderInvoiceStatusEnum.WAITING.getStatus());
        var list = invoiceService.getInvoicePage(reqVO).getList();

        assertEquals(1, list.size(), "未支付订单的申请**不该**出现在待开票列表里（FR-169）");
        assertEquals(paid.getId(), list.get(0).getId());
    }

    @Test
    public void testGetInvoicePage_filterByStatusAndOrderNo() {
        TradeOrderInvoiceDO invoice = prepareWaitingInvoice(7800);

        // 按状态筛：已开票时为 0 条
        TradeOrderInvoicePageReqVO issuedReq = new TradeOrderInvoicePageReqVO();
        issuedReq.setStatus(TradeOrderInvoiceStatusEnum.ISSUED.getStatus());
        assertTrue(invoiceService.getInvoicePage(issuedReq).getList().isEmpty());

        // 按订单号精确查
        TradeOrderInvoicePageReqVO noReq = new TradeOrderInvoicePageReqVO();
        noReq.setOrderNo(invoice.getOrderNo());
        var list = invoiceService.getInvoicePage(noReq).getList();
        assertEquals(1, list.size());
        assertEquals(invoice.getId(), list.get(0).getId());
    }

    // ==================== 订单列表的开票状态（US22 / T411 / FR-178） ====================

    @Test
    public void testGetInvoiceStatusMapByOrderIds() {
        TradeOrderDO withInvoice = createOrder(7800);
        TradeOrderDO issuedOrder = createOrder(7800);
        TradeOrderDO withoutInvoice = createOrder(7800);
        invoiceService.createInvoice(withInvoice, normalInvoice());
        TradeOrderInvoiceDO issued = invoiceMapper.selectById(
                invoiceService.createInvoice(issuedOrder, normalInvoice()));
        invoiceService.issueInvoice(issued.getId(), "https://x.com/a.pdf", 100L);

        Map<Long, Integer> statusMap = invoiceService.getInvoiceStatusMapByOrderIds(List.of(
                withInvoice.getId(), issuedOrder.getId(), withoutInvoice.getId()));

        // 有申请的两笔各带自己的状态；**没申请的那笔不在 map 里**（而不是塞个 null 值）——
        // 页面据此把"未申请"与"申请了"分开（订单列表上不显示任何东西）
        assertEquals(TradeOrderInvoiceStatusEnum.WAITING.getStatus(), statusMap.get(withInvoice.getId()));
        assertEquals(TradeOrderInvoiceStatusEnum.ISSUED.getStatus(), statusMap.get(issuedOrder.getId()));
        assertFalse(statusMap.containsKey(withoutInvoice.getId()));
        assertEquals(2, statusMap.size());
    }

    @Test
    public void testGetInvoiceStatusMapByOrderIds_emptyIds() {
        // 空入参返回空 map（而不是抛异常 / 全表查）—— 订单列表为空时会走到
        assertTrue(invoiceService.getInvoiceStatusMapByOrderIds(List.of()).isEmpty());
    }

    // ==================== 批量查询（订单列表要用，FR-178 的前提） ====================

    @Test
    public void testGetInvoiceListByOrderIds_success() {
        TradeOrderDO order1 = createOrder(7800);
        TradeOrderDO order2 = createOrder(100);
        invoiceService.createInvoice(order1, normalInvoice());
        invoiceService.createInvoice(order2, normalInvoice());

        // 一页里只查其中的两笔，另一笔不存在 → 返回两条；空入参返回空表而不是全表
        assertEquals(2, invoiceService.getInvoiceListByOrderIds(
                List.of(order1.getId(), order2.getId(), 9999L)).size());
        assertTrue(invoiceService.getInvoiceListByOrderIds(List.of()).isEmpty());
    }

}
