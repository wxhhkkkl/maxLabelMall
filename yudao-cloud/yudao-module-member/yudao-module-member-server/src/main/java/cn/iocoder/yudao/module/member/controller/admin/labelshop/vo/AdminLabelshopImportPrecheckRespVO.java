package cn.iocoder.yudao.module.member.controller.admin.labelshop.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 导入前置检查响应。
 */
@Data
public class AdminLabelshopImportPrecheckRespVO {

    private Long orgId;
    private String orgName;
    private Long ownerMemberId;
    private String ownerNickname;
    private String ownerMobile;
    private Integer orgStatus;
    private Boolean licenseActive;
    private LocalDate licenseEndDate;
    private Integer seatCount;
    private String folderName;
    private String personalEdition;
    private Long personalUsedBytes;
    private Long personalQuotaBytes;
    private Long enterpriseUsedBytes;
    private Long enterpriseQuotaBytes;
    private Long runningTaskId;

}
