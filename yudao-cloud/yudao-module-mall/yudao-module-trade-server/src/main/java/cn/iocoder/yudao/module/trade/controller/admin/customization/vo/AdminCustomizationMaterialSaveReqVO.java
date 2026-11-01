package cn.iocoder.yudao.module.trade.controller.admin.customization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 定制材质新增/修改")
@Data
public class AdminCustomizationMaterialSaveReqVO {

    @Schema(description = "编号（修改时必填）", example = "3")
    private Long id;

    @Schema(description = "材质名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "可移除铜版")
    @NotEmpty(message = "材质名称不能为空")
    @Size(max = 64, message = "材质名称最多 64 个字符")
    private String name;

    @Schema(description = "材质说明（展示给客户）", example = "有热敏涂层的平滑亮白纸")
    @Size(max = 1000, message = "材质说明最多 1000 个字符")
    private String description;

    @Schema(description = "材质基价，分/㎡", requiredMode = Schema.RequiredMode.REQUIRED, example = "1138")
    @NotNull(message = "材质基价不能为空")
    @Min(value = 1, message = "材质基价必须大于 0")
    private Integer basePrice;

    @Schema(description = "材质打印系数", requiredMode = Schema.RequiredMode.REQUIRED, example = "4.0")
    @NotNull(message = "材质打印系数不能为空")
    private BigDecimal printCoefficient;

    @Schema(description = "印刷系数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.5")
    @NotNull(message = "印刷系数不能为空")
    private BigDecimal printingCoefficient;

    @Schema(description = "数量下限", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "数量下限不能为空")
    private Integer minQuantity;

    @Schema(description = "数量上限", requiredMode = Schema.RequiredMode.REQUIRED, example = "100000")
    @NotNull(message = "数量上限不能为空")
    private Integer maxQuantity;

    @Schema(description = "成品宽度下限，mm", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "宽度下限不能为空")
    private Integer minWidth;

    @Schema(description = "成品宽度上限，mm", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")
    @NotNull(message = "宽度上限不能为空")
    private Integer maxWidth;

    @Schema(description = "成品高度下限，mm", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "高度下限不能为空")
    private Integer minHeight;

    @Schema(description = "成品高度上限，mm", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")
    @NotNull(message = "高度上限不能为空")
    private Integer maxHeight;

    @Schema(description = "状态：0 启用 1 停用", example = "0")
    private Integer status;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "面积区间（必须连续覆盖）", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotEmpty(message = "面积区间不能为空")
    private List<AdminCustomizationAreaRuleSaveReqVO> areaRules;

}
