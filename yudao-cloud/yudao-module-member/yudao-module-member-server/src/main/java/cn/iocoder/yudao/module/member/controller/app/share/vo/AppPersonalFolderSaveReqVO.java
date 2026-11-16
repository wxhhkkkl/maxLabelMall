package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 个人空间文件夹创建 Request VO")
@Data
public class AppPersonalFolderSaveReqVO {

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = 64, message = "文件夹名称不能超过 64 个字符")
    private String name;

    @Schema(description = "父文件夹编号；空 = 根目录")
    private Long parentId;

}
