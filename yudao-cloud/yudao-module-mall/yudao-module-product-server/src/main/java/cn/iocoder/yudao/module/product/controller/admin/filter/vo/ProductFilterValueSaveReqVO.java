package cn.iocoder.yudao.module.product.controller.admin.filter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 筛选维度可选值 新增/修改 Request VO")
@Data
public class ProductFilterValueSaveReqVO {

    @Schema(description = "编号", example = "3")
    private Long id;

    @Schema(description = "所属筛选维度编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    @NotNull(message = "所属维度不能为空")
    private Long dimensionId;

    @Schema(description = "可选值名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "110")
    @NotEmpty(message = "可选值名称不能为空")
    @Size(max = 32, message = "可选值名称长度不能超过 32 个字符")
    private String name;

    @Schema(description = "展示顺序", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;

}
