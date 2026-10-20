package cn.iocoder.yudao.module.member.api.subscription.dto;

import lombok.Data;

/**
 * 发放会员订阅 Request DTO（由 trade 支付成功/退款处理器调用）
 */
@Data
public class MemberSubscriptionGrantReqDTO {

    private Long memberId;
    private Long orderId;
    private Long orderItemId;
    private String orderNo;
    private Long planId;
    private Long spuId;
    private Long skuId;
    /**
     * 单个 SKU 的周期（月）
     */
    private Integer periodMonths;
    /**
     * 订单项数量
     */
    private Integer count;

}
