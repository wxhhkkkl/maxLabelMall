package cn.iocoder.yudao.module.member.controller.app.share.vo;

import cn.iocoder.yudao.module.member.service.share.dto.ShareFolderMemberInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 企业文件夹可见成员列表 Response VO")
@Data
public class AppShareFolderMemberListRespVO {

    private Long folderId;

    private List<ShareFolderMemberInfo> members;

}
