package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 共享内容 Response VO")
@Data
public class AppShareContentRespVO {

    private Long id;
    private Integer scope;
    private Long folderId;
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
    private Boolean canDelete;

}
