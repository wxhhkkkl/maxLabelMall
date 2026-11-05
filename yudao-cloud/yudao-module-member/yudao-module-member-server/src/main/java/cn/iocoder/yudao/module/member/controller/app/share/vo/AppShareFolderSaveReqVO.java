package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 企业文件夹保存 Request VO")
@Data
public class AppShareFolderSaveReqVO {

    @Schema(description = "编号（更新时必填）")
    private Long id;

    @Schema(description = "文件夹名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = 64, message = "文件夹名称不能超过 64 个字符")
    private String name;

}
