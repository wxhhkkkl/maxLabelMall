package cn.iocoder.yudao.module.member.service.labelshop.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 导入前的目标与前置条件。
 */
@Data
public class LabelshopPrecheck {

    private Long orgId;
    private String orgName;
    private Long ownerMemberId;
    private String ownerNickname;
    private String ownerMobileMasked;
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
