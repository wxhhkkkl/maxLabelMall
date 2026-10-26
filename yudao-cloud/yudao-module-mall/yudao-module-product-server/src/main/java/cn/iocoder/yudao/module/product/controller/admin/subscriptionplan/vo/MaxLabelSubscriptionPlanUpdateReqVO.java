package cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "管理后台 - 会员授权计划更新 Request VO")
@Data
public class MaxLabelSubscriptionPlanUpdateReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "11")
    @NotNull(message = "编号不能为空")
    private Long id;

    @Schema(description = "授予周期（月）", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    @NotNull(message = "授予周期不能为空")
    @Min(value = 1, message = "授予周期必须大于等于 1 个月")
    @Max(value = 120, message = "授予周期必须小于等于 120 个月")
    private Integer periodMonths;

    @Schema(description = "类别：1 个人 2 企业", example = "1")
    private Integer scope;

    @Schema(description = "桌面端版本：standard / professional / enterprise；不传则保持原值", example = "professional")
    @Pattern(regexp = "standard|professional|enterprise", message = "桌面端版本不合法")
    private String edition;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;

    @Schema(description = "排序", example = "1")
    private Integer sort;

}
