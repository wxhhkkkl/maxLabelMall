package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 共享空间概览 Response VO")
@Data
public class ShareSpaceOverviewRespVO {

    private Personal personal;

    private Enterprise enterprise;

    @Schema(description = "个人空间")
    @Data
    public static class Personal {
        private String edition;
        private Long quotaBytes;
        private Long usedBytes;
        private Long remainingBytes;
        private Boolean canUpload;
    }

    @Schema(description = "企业空间；未加入企业时为 null")
    @Data
    public static class Enterprise {
        private Long orgId;
        private String orgName;
        private String edition;
        private Boolean seatActive;
        private Boolean licenseActive;
        private Boolean isAdmin;
        private Long quotaBytes;
        private Long usedBytes;
        private Long remainingBytes;
        private Boolean canUpload;
        private Boolean readOnly;
    }

}
