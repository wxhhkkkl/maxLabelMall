package cn.iocoder.yudao.module.trade.service.invoice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.admin.invoice.vo.TradeOrderInvoicePageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.invoice.vo.TradeOrderInvoiceRespVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppTradeOrderInvoiceCreateReqVO;
import cn.iocoder.yudao.module.trade.convert.invoice.InvoiceConvert;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeOrderInvoiceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.invoice.TradeOrderInvoiceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.invoice.TradeOrderInvoiceStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

/**
 * 交易订单的开票申请 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class TradeOrderInvoiceServiceImpl implements TradeOrderInvoiceService {

    /**
     * 允许的发票文件扩展名（FR-171）
     *
     * ⚠️ **服务端必须自己判**：前端 `accept` 只是给浏览器的过滤，直接把 URL 传给接口就能绕过。
     */
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    @Resource
    private TradeOrderInvoiceMapper invoiceMapper;
    @Resource
    private TradeOrderMapper orderMapper;

    @Override
    public Long createInvoice(TradeOrderDO order, AppInvoiceApplyBaseVO invoice) {
        // 1. 不开票：什么都不做 —— 这是**默认路径**，行为必须与今天逐字一致（FR-159）
        if (invoice == null) {
            return null;
        }
        // 2. 实付 0 元（全额券抵扣）：**忽略而不是拒绝**（spec 的 Edge Case / Q33 同一精神）
        //    ⚠️ 这条不能只在界面上挡：0 元订单同样满足"已支付"，绕过界面的调用方会把它塞进来，
        //    而一张 0 元的发票没有任何意义
        if (order.getPayPrice() == null || order.getPayPrice() <= 0) {
            return null;
        }
        return doCreateInvoice(order, invoice);
    }

    /**
     * 订单详情**补开**（US20）
     *
     * 七条校验逐条给出**说得清的理由**；其中「不属于当前会员」这条**报"订单不存在"**，
     * 而不是"无权访问" —— 后者会泄露"这个订单号是存在的"（枚举订单号就成了攻击面）。
     */
    @Override
    public Long createInvoiceByMember(Long userId, AppTradeOrderInvoiceCreateReqVO reqVO) {
        // 1. 订单存在、且属于当前会员
        TradeOrderDO order = orderMapper.selectById(reqVO.getOrderId());
        if (order == null || !ObjectUtil.equal(order.getUserId(), userId)) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 2. 已支付
        if (!Boolean.TRUE.equals(order.getPayStatus())) {
            throw exception(INVOICE_ORDER_NOT_PAID);
        }
        // 3. 实付 > 0 —— ⚠️ 与下单路径**刻意不同**：那里是"忽略"（订单照下），
        //    这里是"拒绝"（订单已经存在了，给个明确理由就好）
        if (order.getPayPrice() == null || order.getPayPrice() <= 0) {
            throw exception(INVOICE_ORDER_AMOUNT_ZERO);
        }
        // 4. 未取消：已支付之后又被取消（全额退款）的订单，票不该再开
        if (TradeOrderStatusEnum.isCanceled(order.getStatus())) {
            throw exception(INVOICE_ORDER_CANCELED);
        }
        // 5. 一单一条 + 抬头校验 + 落库（与下单路径共用同一段代码）
        return doCreateInvoice(order, reqVO);
    }

    /**
     * 两条入口共用的落库：一单一条 → 票种与抬头校验 → 快照落库
     *
     * ⚠️ 它**不含**"实付是否为 0"的判断 —— 那一条两条路径的行为不同（忽略 vs 拒绝），由调用方决定。
     */
    private Long doCreateInvoice(TradeOrderDO order, AppInvoiceApplyBaseVO invoice) {
        // 一单一条（FR-160）：服务层先挡，给一个说得清的报错；并发下由 uk_order_id 兜底
        if (invoiceMapper.selectByOrderId(order.getId()) != null) {
            throw exception(INVOICE_ORDER_EXISTS);
        }
        // 校验票种与抬头（唯一一处规则，下单与补开共用）
        InvoiceValidator.validateApply(invoice);

        // 落库：抬头是**快照**（FR-158），金额取**订单实付**（FR-161，请求里没有这个字段）
        TradeOrderInvoiceDO invoiceDO = BeanUtils.toBean(invoice, TradeOrderInvoiceDO.class);
        invoiceDO.setOrderId(order.getId());
        invoiceDO.setOrderNo(order.getNo());
        invoiceDO.setUserId(order.getUserId());
        invoiceDO.setAmount(order.getPayPrice());
        invoiceDO.setStatus(TradeOrderInvoiceStatusEnum.WAITING.getStatus());
        try {
            invoiceMapper.insert(invoiceDO);
        } catch (DuplicateKeyException e) {
            // 并发下两次"申请"双双通过了上面的检查，被唯一索引拦下 ——
            // 转成业务错误，别把数据库异常原样抛成 500
            throw exception(INVOICE_ORDER_EXISTS);
        }
        return invoiceDO.getId();
    }

    @Override
    public TradeOrderInvoiceDO getInvoiceByOrderId(Long orderId) {
        return invoiceMapper.selectByOrderId(orderId);
    }

    @Override
    public List<TradeOrderInvoiceDO> getInvoiceListByOrderIds(Collection<Long> orderIds) {
        // ⚠️ 空集合必须提前返回：MyBatis-Plus 会拼出 `IN ()`，那是语法错误
        if (CollUtil.isEmpty(orderIds)) {
            return Collections.emptyList();
        }
        return invoiceMapper.selectListByOrderIds(orderIds);
    }

    @Override
    public Map<Long, Integer> getInvoiceStatusMapByOrderIds(Collection<Long> orderIds) {
        // 一次 IN 查完一页订单（risk R3：**不许逐笔查** —— 一页 12 条会变成 13 次查询）
        return convertMap(getInvoiceListByOrderIds(orderIds),
                TradeOrderInvoiceDO::getOrderId, TradeOrderInvoiceDO::getStatus);
    }

    // ==================== 后台：开具 / 分页 / 作废 ====================

    @Override
    public void issueInvoice(Long id, String fileUrl, Long userId) {
        // 1. 申请存在
        TradeOrderInvoiceDO invoice = invoiceMapper.selectById(id);
        if (invoice == null) {
            throw exception(INVOICE_NOT_FOUND);
        }
        // 2. 已作废的不许开具（Q30）——「已开票」的允许（那是重新上传，Q31）
        if (TradeOrderInvoiceStatusEnum.CANCELED.getStatus().equals(invoice.getStatus())) {
            throw exception(INVOICE_ISSUE_FAIL_STATUS_CANCELED);
        }
        // 3. 订单**必须已支付**：列表已经过滤过了，但**接口不能只靠列表**（直连进来照样要拒）
        TradeOrderDO order = orderMapper.selectById(invoice.getOrderId());
        if (order == null || !Boolean.TRUE.equals(order.getPayStatus())) {
            throw exception(INVOICE_ISSUE_FAIL_ORDER_NOT_PAID);
        }
        // 4. 文件类型白名单（FR-171）：拒的时候**状态与文件都不变**
        if (!isAllowedInvoiceFile(fileUrl)) {
            throw exception(INVOICE_FILE_TYPE_NOT_ALLOWED);
        }

        // 5. 覆盖式写入（Q31：只留最新一份，不保留历次版本）
        TradeOrderInvoiceDO update = new TradeOrderInvoiceDO();
        update.setId(id);
        update.setStatus(TradeOrderInvoiceStatusEnum.ISSUED.getStatus());
        update.setInvoiceFileUrl(fileUrl);
        update.setIssueTime(LocalDateTime.now());
        update.setIssueUserId(userId);
        invoiceMapper.updateById(update);
    }

    /**
     * 文件扩展名是否可接受（FR-171）
     *
     * 从 URL 里取**最后一个点之后的段**，去掉 query / hash，并**大小写不敏感**
     * （客户从微信里存下来的文件常是 `.PDF`，为这个误拒会让人莫名其妙）。
     */
    private boolean isAllowedInvoiceFile(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return false;
        }
        String path = fileUrl.split("[?#]")[0];
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return false;
        }
        return ALLOWED_FILE_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase());
    }

    @Override
    public void cancelInvoiceByOrderId(Long orderId) {
        // 待开票与**已开票**的都会作废（2026-10-15 所有者口径：退款了票就该撤回）；
        // 条件只排掉已经作废的 —— 那条让本方法**幂等**（逐订单项 + 整单两条钩子都会调它）
        int updated = invoiceMapper.updateStatusToCanceledByOrderId(orderId, LocalDateTime.now());
        if (updated > 0) {
            log.info("[cancelInvoiceByOrderId][订单({}) 的开票申请已作废]", orderId);
        }
    }

    @Override
    public PageResult<TradeOrderInvoiceRespVO> getInvoicePage(TradeOrderInvoicePageReqVO reqVO) {
        // 待开票（与不限状态）只看已支付订单的申请 —— 过滤在 SQL 里（FR-169），不在内存里
        PageResult<TradeOrderInvoiceDO> page = invoiceMapper.selectPage(reqVO);
        if (CollUtil.isEmpty(page.getList())) {
            return PageResult.empty(page.getTotal());
        }
        // 一次批量取订单：拿"当前的实付 / 已退"并排展示（Q33，部分退款要靠它判断）
        Map<Long, TradeOrderDO> orderMap = convertMap(
                orderMapper.selectByIds(convertSet(page.getList(), TradeOrderInvoiceDO::getOrderId)),
                TradeOrderDO::getId);
        return new PageResult<>(convertList(page.getList(),
                invoice -> InvoiceConvert.convertToAdminRespVO(invoice, orderMap.get(invoice.getOrderId()))),
                page.getTotal());
    }

}
