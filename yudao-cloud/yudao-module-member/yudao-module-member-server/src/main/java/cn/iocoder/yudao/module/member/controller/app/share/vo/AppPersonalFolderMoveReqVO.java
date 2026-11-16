package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "用户 App - 个人空间文件夹移动 Request VO")
@Data
public class AppPersonalFolderMoveReqVO {

    @Schema(description = "文件夹编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件夹编号不能为空")
    private Long id;

    @Schema(description = "目标父文件夹编号；空 = 根目录")
    private Long parentId;

}
