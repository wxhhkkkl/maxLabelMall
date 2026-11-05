package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 企业共享空间概览 Response VO")
@Data
public class AdminOrgShareOverviewRespVO {

    private Long quotaBytes;
    private Long usedBytes;
    private Long remainingBytes;

}
