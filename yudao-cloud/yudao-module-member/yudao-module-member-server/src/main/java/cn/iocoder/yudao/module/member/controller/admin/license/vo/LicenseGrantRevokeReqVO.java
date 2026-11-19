package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 撤回赠送 Request VO（020 / Q164）
 *
 * 撤回是**收回客户已有权益**，理由必填、留痕不可删。
 */
@Data
public class LicenseGrantRevokeReqVO {

    @Schema(description = "赠送记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "501")
    @NotNull(message = "赠送记录编号不能为空")
    private Long id;

    @Schema(description = "撤回理由", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "误送给该客户，实际应为另一家")
    @NotBlank(message = "请填写撤回理由")
    @Size(max = 255, message = "撤回理由不能超过 255 字")
    private String reason;

}
