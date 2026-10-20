package cn.iocoder.yudao.module.product.controller.app.subscriptionplan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 会员授权周期 Response VO")
@Data
public class AppMaxLabelSubscriptionPlanRespVO {

    private Long spuId;
    private Long skuId;
    private String name;
    private Integer periodMonths;
    private Integer price;
    private String picUrl;
    private Integer stock;
    private Integer sort;

}
