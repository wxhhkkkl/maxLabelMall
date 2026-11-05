package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 企业共享内容 Response VO")
@Data
public class AdminOrgShareContentRespVO {

    private Long id;
    private Long folderId;
    private String folderName;
    private Integer contentType;
    private String name;
    private Long sizeBytes;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String category;
    private String keywords;
    private Long uploaderMemberId;
    private String uploaderNickname;
    private LocalDateTime createTime;

}
