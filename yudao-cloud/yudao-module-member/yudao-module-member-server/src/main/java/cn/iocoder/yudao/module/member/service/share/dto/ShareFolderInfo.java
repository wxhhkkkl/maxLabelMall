package cn.iocoder.yudao.module.member.service.share.dto;

import lombok.Data;

/**
 * 企业文件夹列表项。
 */
@Data
public class ShareFolderInfo {

    private Long id;
    private String name;
    private Integer memberCount;
    /** 仅管理员返回 */
    private Integer contentCount;
    /** 仅管理员返回 */
    private Long usedBytes;
    private Boolean canManage;

}
