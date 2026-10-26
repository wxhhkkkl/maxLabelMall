package cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 会员授权计划 Response VO")
@Data
public class MaxLabelSubscriptionPlanRespVO {

    private Long id;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuName;
    private Integer price;
    private Integer periodMonths;
    private Integer scope;
    private Boolean enabled;
    private Integer sort;
    private LocalDateTime createTime;

}
