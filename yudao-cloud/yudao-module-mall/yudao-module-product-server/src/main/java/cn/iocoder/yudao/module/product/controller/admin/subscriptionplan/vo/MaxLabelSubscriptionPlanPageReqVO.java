package cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 会员授权计划分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaxLabelSubscriptionPlanPageReqVO extends PageParam {

    @Schema(description = "商品 SPU 编号", example = "300")
    private Long spuId;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

}
