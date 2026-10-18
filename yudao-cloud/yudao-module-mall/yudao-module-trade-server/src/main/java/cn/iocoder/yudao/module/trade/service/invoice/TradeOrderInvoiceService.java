package cn.iocoder.yudao.module.trade.service.invoice;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.controller.admin.invoice.vo.TradeOrderInvoicePageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.invoice.vo.TradeOrderInvoiceRespVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppTradeOrderInvoiceCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeOrderInvoiceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 交易订单的开票申请 Service
 *
 * @author 芋道源码
 */
public interface TradeOrderInvoiceService {

    /**
     * 下单时创建开票申请 —— **与订单在同一事务里**（由 `TradeOrderUpdateServiceImpl.createOrder` 调用）
     *
     * @param order   订单（已落库，含主键、订单号、实付金额）
     * @param invoice 开票信息；**为 null 表示不开票**，此时不产生任何记录（FR-159）
     * @return 申请编号；没开票或订单实付为 0 时返回 null
     */
    Long createInvoice(TradeOrderDO order, AppInvoiceApplyBaseVO invoice);

    /**
     * 订单详情**补开**（US20）—— 客户事后自助提交，服务端逐条校验（见实现类的注释）
     *
     * @param userId 当前登录会员（订单不属于他时按"订单不存在"处理）
     * @param reqVO  含 orderId 的开票信息
     */
    Long createInvoiceByMember(Long userId, AppTradeOrderInvoiceCreateReqVO reqVO);

    /**
     * 按订单查询开票申请（订单详情用）
     *
     * @return 没有申请时返回 null —— 前端据此决定"整块不显示"还是"给申请入口"（FR-176）
     */
    TradeOrderInvoiceDO getInvoiceByOrderId(Long orderId);

    /**
     * 按订单编号**批量**查询（订单列表用，一次 `IN` 查完一页 —— 不许逐笔查，FR-178）
     */
    List<TradeOrderInvoiceDO> getInvoiceListByOrderIds(Collection<Long> orderIds);

    /**
     * 按订单编号批量取**开票状态**（订单列表要显示它，FR-178）
     *
     * ⚠️ 返回的 map 里**只有"有申请"的订单**；没申请的订单**不在 map 里**（而不是塞一个 null 值）——
     * 页面用 `map.get(id)` 拿到 null 就表示"未申请"，与"申请了"天然分开。
     */
    Map<Long, Integer> getInvoiceStatusMapByOrderIds(Collection<Long> orderIds);

    /**
     * 订单**取消**（含全额退款）时作废其尚未开具的申请（Q30 / FR-184）
     *
     * 由 `TradeInvoiceOrderHandler` 在既有的 `afterCancelOrder` 扩展点里调用 ——
     * 所以 C 端取消、系统超时取消、售后全额退款取消三条路一次覆盖，取消逻辑一行不改。
     * 已经**开具**的申请不动：票已在客户手里，系统不替财务撤回。
     */
    void cancelInvoiceByOrderId(Long orderId);

    /**
     * 后台**开具**：把开好的发票文件挂到申请上，状态变「已开票」（FR-170）
     *
     * @param fileUrl 发票文件地址（前端先传到 infra 文件服务拿到 URL）
     * @param userId  操作人（后台登录用户）
     */
    void issueInvoice(Long id, String fileUrl, Long userId);

    /**
     * 后台分页 —— **待开票列表只含已支付订单**的申请（FR-169）
     *
     * 返回的 VO 里同时带**订单当前的实付 / 已退金额**：`amount` 是申请时的快照，
     * 部分退款不会改它，运营开具前要靠这两个数判断（Q33）。
     */
    PageResult<TradeOrderInvoiceRespVO> getInvoicePage(TradeOrderInvoicePageReqVO reqVO);

}
