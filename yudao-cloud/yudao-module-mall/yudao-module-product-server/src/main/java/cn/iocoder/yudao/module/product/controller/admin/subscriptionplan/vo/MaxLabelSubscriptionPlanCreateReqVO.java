package cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 会员授权计划创建 Request VO")
@Data
public class MaxLabelSubscriptionPlanCreateReqVO {

    @Schema(description = "商品 SPU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "300")
    @NotNull(message = "商品 SPU 编号不能为空")
    private Long spuId;

    @Schema(description = "商品 SKU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "301")
    @NotNull(message = "商品 SKU 编号不能为空")
    private Long skuId;

    @Schema(description = "授予周期（月）", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    @NotNull(message = "授予周期不能为空")
    @Min(value = 1, message = "授予周期必须大于等于 1 个月")
    @Max(value = 120, message = "授予周期必须小于等于 120 个月")
    private Integer periodMonths;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;

    @Schema(description = "排序", example = "1")
    private Integer sort;

}
