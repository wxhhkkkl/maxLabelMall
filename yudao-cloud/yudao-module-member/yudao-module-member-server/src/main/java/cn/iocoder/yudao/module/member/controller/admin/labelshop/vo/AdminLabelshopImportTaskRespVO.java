package cn.iocoder.yudao.module.member.controller.admin.labelshop.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导入任务详情 / 最近任务。
 */
@Data
public class AdminLabelshopImportTaskRespVO {

    private Long id;
    /** 导入对象：1 企业 2 个人会员 */
    private Integer subjectType;
    private Long orgId;
    private String orgName;
    private Long ownerMemberId;
    private Long folderId;
    private String folderName;
    private String sourceAccountMasked;
    /** 0 待执行 1 进行中 2 已完成 3 已失败 4 已中断 */
    private Integer status;
    private String phase;
    private Integer totalCount;
    private Integer processedCount;
    private Integer successCount;
    private Integer skipCount;
    private Integer failCount;
    private Long personalBytes;
    private Long enterpriseBytes;
    private Long personalUsedBytes;
    private Long personalQuotaBytes;
    private Boolean personalExceeded;
    private Long enterpriseUsedBytes;
    private Long enterpriseQuotaBytes;
    private Boolean enterpriseExceeded;
    private List<AdminLabelshopImportLibraryStatVO> libraries;
    private Boolean uncleaned;
    private Long archiveJsonCount;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private String errorMessage;

}
