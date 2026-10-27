package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 软件创建/修改 Request VO")
@Data
public class AdminSoftwareSaveReqVO {

    @Schema(description = "编号（修改时必填）")
    private Long id;

    @Schema(description = "分类编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题不能超过 128 个字符")
    private String title;

    @Schema(description = "介绍")
    @Size(max = 1024, message = "介绍不能超过 1024 个字符")
    private String introduction;

    @Schema(description = "类型：1 桌面端软件 2 打印驱动", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "类型不能为空")
    private Integer type;

    @Schema(description = "是否在服务支持页展示")
    private Boolean showInSupport;

    @Schema(description = "状态：0 启用 1 停用")
    private Integer status;

}
