package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 软件分类创建/修改 Request VO")
@Data
public class AdminSoftwareCategorySaveReqVO {

    @Schema(description = "编号（修改时必填）")
    private Long id;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称不能超过 64 个字符")
    private String name;

    @Schema(description = "排序（升序）")
    private Integer sort;

    @Schema(description = "状态：0 启用 1 停用")
    private Integer status;

}
