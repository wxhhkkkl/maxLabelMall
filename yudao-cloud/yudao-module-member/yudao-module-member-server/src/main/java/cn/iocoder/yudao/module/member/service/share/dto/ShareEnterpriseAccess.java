package cn.iocoder.yudao.module.member.service.share.dto;

import lombok.Data;

/**
 * 企业空间访问上下文。
 */
@Data
public class ShareEnterpriseAccess {

    private Long orgId;
    private String orgName;
    /** 企业状态：0 启用 1 停用 */
    private Integer orgStatus;
    /** 席位分配未撤销 */
    private Boolean seatActive;
    /** 企业启用且存在未过期未退款批次 */
    private Boolean licenseActive;
    /** OWNER / ADMIN */
    private Boolean isAdmin;

    /** 只读 = 授权无效；仍可浏览、下载、删除 */
    public boolean isReadOnly() {
        return !Boolean.TRUE.equals(licenseActive);
    }

    /** 可写 = 有席位 + 授权有效 */
    public boolean isWritable() {
        return Boolean.TRUE.equals(seatActive) && Boolean.TRUE.equals(licenseActive);
    }

}
