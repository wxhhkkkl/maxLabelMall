package cn.iocoder.yudao.module.member.service.share.dto;

import lombok.Data;

/**
 * 文件夹可见名单编辑项。
 */
@Data
public class ShareFolderMemberInfo {

    private Long memberId;
    private String nickname;
    private String mobile;
    private Integer role;
    private Boolean visible;

}
