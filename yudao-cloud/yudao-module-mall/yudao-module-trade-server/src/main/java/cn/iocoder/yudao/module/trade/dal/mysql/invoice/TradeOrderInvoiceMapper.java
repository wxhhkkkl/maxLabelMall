package cn.iocoder.yudao.module.trade.dal.mysql.invoice;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.controller.admin.invoice.vo.TradeOrderInvoicePageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeOrderInvoiceDO;
import cn.iocoder.yudao.module.trade.enums.invoice.TradeOrderInvoiceStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface TradeOrderInvoiceMapper extends BaseMapperX<TradeOrderInvoiceDO> {

    /**
     * 后台「开票申请」分页
     *
     * ⚠️ **待开票（与"不限状态"）只看订单已支付的申请**（FR-169）：用一个子查询把订单的支付状态带进来，
     * 而不是先分页再在内存里过滤 —— 后者会让**每页条数不对**（过滤掉几条就少几条，甚至整页为空）。
     */
    default PageResult<TradeOrderInvoiceDO> selectPage(TradeOrderInvoicePageReqVO reqVO) {
        boolean onlyPaidOrder = reqVO.getStatus() == null
                || TradeOrderInvoiceStatusEnum.WAITING.getStatus().equals(reqVO.getStatus());
        return selectPage(reqVO, new LambdaQueryWrapperX<TradeOrderInvoiceDO>()
                .eqIfPresent(TradeOrderInvoiceDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TradeOrderInvoiceDO::getOrderNo, reqVO.getOrderNo())
                .inSql(onlyPaidOrder, TradeOrderInvoiceDO::getOrderId,
                        "SELECT id FROM trade_order WHERE pay_status = TRUE AND deleted = FALSE")
                .orderByDesc(TradeOrderInvoiceDO::getId));
    }

    /**
     * 按订单查询申请（一笔订单至多一条）
     */
    default TradeOrderInvoiceDO selectByOrderId(Long orderId) {
        return selectOne(TradeOrderInvoiceDO::getOrderId, orderId);
    }

    /**
     * 按订单编号**批量**查询 —— 订单列表要用它带出每笔订单的开票状态（FR-178）
     *
     * ⚠️ 必须是批量：一次 `IN` 查完一页订单，**不许逐笔查**（N+1）。
     */
    default List<TradeOrderInvoiceDO> selectListByOrderIds(Collection<Long> orderIds) {
        return selectList(TradeOrderInvoiceDO::getOrderId, orderIds);
    }

    /**
     * 按订单查询**待开票**的申请 —— 作废时用（只动待开票的，已开票的不许被动，Q30）
     */
    default TradeOrderInvoiceDO selectWaitingByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<TradeOrderInvoiceDO>()
                .eq(TradeOrderInvoiceDO::getOrderId, orderId)
                .eq(TradeOrderInvoiceDO::getStatus, TradeOrderInvoiceStatusEnum.WAITING.getStatus()));
    }

    /**
     * 把该订单的开票申请置为已作废（订单取消 / 退款时由 {@code TradeInvoiceOrderHandler} 调用）
     *
     * ⚠️ 条件只排掉**已经作废**的（`ne CANCELED`），**不排除已开票的**：
     * 2026-10-15 所有者的口径是「**退款了票就该撤回**」—— 前台不再显示、后台也不能再更新。
     * `cancel_time` 与 `invoice_file_url` 都保留：前者用于展示"什么时候撤的"，
     * 后者是财务核对/红冲时要查的凭据。
     *
     * ⚠️ 幂等：取消路径上 handler 会被调用多次（逐订单项 + 整单），
     * 已经作废过的再调一次影响 0 行 —— **不是异常**。
     *
     * @return 更新行数；0 表示这条订单没有需要作废的申请
     */
    default int updateStatusToCanceledByOrderId(Long orderId, LocalDateTime cancelTime) {
        return update(new LambdaUpdateWrapper<TradeOrderInvoiceDO>()
                .eq(TradeOrderInvoiceDO::getOrderId, orderId)
                .ne(TradeOrderInvoiceDO::getStatus, TradeOrderInvoiceStatusEnum.CANCELED.getStatus())
                .set(TradeOrderInvoiceDO::getStatus, TradeOrderInvoiceStatusEnum.CANCELED.getStatus())
                .set(TradeOrderInvoiceDO::getCancelTime, cancelTime));
    }

}
