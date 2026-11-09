package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderPayReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderPayRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderPayStatusRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderSettlementReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderCreateRespVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementRespVO;

/**
 * 代客下单 Service（017）
 */
public interface AgentOrderService {

    /**
     * 代客结算预览
     */
    AppTradeOrderSettlementRespVO settlement(AdminAgentOrderSettlementReqVO reqVO);

    /**
     * 创建代客订单
     *
     * @param operatorUserId 当前后台用户编号（代下单人）
     */
    TradeOrderCreateRespVO create(Long operatorUserId, AdminAgentOrderCreateReqVO reqVO);

    /**
     * 为代客订单发起在线支付
     */
    AdminAgentOrderPayRespVO pay(AdminAgentOrderPayReqVO reqVO);

    /**
     * 查询代客订单支付状态（页面轮询）
     */
    AdminAgentOrderPayStatusRespVO getPayStatus(Long orderId);

}
