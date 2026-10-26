package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgLicenseGrantReqDTO;
import cn.iocoder.yudao.module.member.api.subscription.MemberSubscriptionApi;
import cn.iocoder.yudao.module.member.api.subscription.dto.MemberSubscriptionGrantReqDTO;
import cn.iocoder.yudao.module.product.api.subscriptionplan.ProductSubscriptionPlanApi;
import cn.iocoder.yudao.module.product.api.subscriptionplan.dto.ProductSubscriptionPlanRespDTO;
import cn.iocoder.yudao.module.product.enums.subscriptionplan.MaxLabelSubscriptionScopeEnum;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MaxLabel 会员订阅的 {@link TradeOrderHandler} 实现类。
 *
 * 支付成功时按订单项 SKU 解析会员周期并发放；整单/订单项退款时只标记来源已退款，不回收周期。
 * ⚠️ afterPayOrder 收到的 order 是 CAS 之前加载的，不能读 payStatus/status。
 */
@Component
public class TradeMaxLabelSubscriptionOrderHandler implements TradeOrderHandler {

    @Resource
    private ProductSubscriptionPlanApi productSubscriptionPlanApi;
    @Resource
    private MemberSubscriptionApi memberSubscriptionApi;
    @Resource
    private MemberOrgApi memberOrgApi;

    @Override
    public void afterPayOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }
        for (TradeOrderItemDO orderItem : orderItems) {
            ProductSubscriptionPlanRespDTO plan = productSubscriptionPlanApi
                    .getBySkuId(orderItem.getSkuId()).getCheckedData();
            if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
                continue;
            }
            // 企业计划：发放到下单时快照的企业名下
            if (MaxLabelSubscriptionScopeEnum.ENTERPRISE.getScope().equals(plan.getScope())) {
                if (order.getMaxlabelOrgId() == null) {
                    // 不应发生：下单时已校验并快照。直接抛错，避免静默丢权益
                    throw new IllegalStateException("企业授权订单缺少 maxlabelOrgId: " + order.getId());
                }
                MemberOrgLicenseGrantReqDTO orgReqDTO = new MemberOrgLicenseGrantReqDTO();
                orgReqDTO.setOrgId(order.getMaxlabelOrgId());
                orgReqDTO.setOrderId(order.getId());
                orgReqDTO.setOrderItemId(orderItem.getId());
                orgReqDTO.setOrderNo(order.getNo());
                orgReqDTO.setPlanId(plan.getId());
                orgReqDTO.setSpuId(orderItem.getSpuId());
                orgReqDTO.setSkuId(orderItem.getSkuId());
                orgReqDTO.setPeriodMonths(plan.getPeriodMonths());
                orgReqDTO.setSeatsTotal(orderItem.getCount());
                memberOrgApi.grantLicense(orgReqDTO).checkError();
                continue;
            }
            MemberSubscriptionGrantReqDTO reqDTO = new MemberSubscriptionGrantReqDTO();
            reqDTO.setMemberId(order.getUserId());
            reqDTO.setOrderId(order.getId());
            reqDTO.setOrderItemId(orderItem.getId());
            reqDTO.setOrderNo(order.getNo());
            reqDTO.setPlanId(plan.getId());
            reqDTO.setSpuId(orderItem.getSpuId());
            reqDTO.setSkuId(orderItem.getSkuId());
            reqDTO.setPeriodMonths(plan.getPeriodMonths());
            reqDTO.setCount(orderItem.getCount());
            memberSubscriptionApi.grant(reqDTO).checkError();
        }
    }

    @Override
    public void afterCancelOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        memberSubscriptionApi.markRefundedByOrder(order.getId()).checkError();
    }

    @Override
    public void afterCancelOrderItem(TradeOrderDO order, TradeOrderItemDO orderItem) {
        memberSubscriptionApi.markRefundedByOrderItem(orderItem.getId()).checkError();
    }

}
