package cn.iocoder.yudao.module.product.api.subscriptionplan.dto;

import lombok.Data;

/**
 * 会员授权计划 Response DTO（供 trade 在支付成功后解析 SKU 对应的周期）
 */
@Data
public class ProductSubscriptionPlanRespDTO {

    private Long id;
    private Long spuId;
    private Long skuId;
    private Integer periodMonths;
    private Boolean enabled;

}
