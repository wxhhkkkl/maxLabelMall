package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 企业文件夹可见成员保存 Request VO")
@Data
public class AppShareFolderMemberReqVO {

    @Schema(description = "文件夹编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件夹编号不能为空")
    private Long id;

    @Schema(description = "可见成员编号列表；空数组 = 仅管理员可见")
    private List<Long> memberIds;

}
