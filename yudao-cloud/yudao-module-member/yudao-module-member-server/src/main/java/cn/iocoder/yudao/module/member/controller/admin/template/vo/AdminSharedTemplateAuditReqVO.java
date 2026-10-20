package cn.iocoder.yudao.module.member.controller.admin.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 共享模板驳回 Request VO")
@Data
public class AdminSharedTemplateAuditReqVO {

    @Schema(description = "共享模板编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "共享模板编号不能为空")
    private Long id;

    @Schema(description = "驳回原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 255, message = "驳回原因不能超过 255 个字符")
    private String reason;

}
