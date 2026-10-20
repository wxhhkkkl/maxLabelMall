package cn.iocoder.yudao.module.member.controller.admin.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 共享模板 Response VO")
@Data
public class AdminSharedTemplateRespVO {

    private Long id;
    private Long sourceTemplateId;
    private Long publisherMemberId;
    private String publisherNickname;
    private String publisherMobile;
    private String name;
    private Integer sizeBytes;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String category;
    private String keywords;
    private Integer auditStatus;
    private String rejectReason;
    private Long auditUserId;
    private LocalDateTime auditTime;
    private LocalDateTime publishedTime;
    private LocalDateTime createTime;
    private String content;

}
