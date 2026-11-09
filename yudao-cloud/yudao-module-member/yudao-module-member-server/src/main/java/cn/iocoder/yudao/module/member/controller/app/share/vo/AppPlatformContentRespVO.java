package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 平台共享内容 Response VO")
@Data
public class AppPlatformContentRespVO {

    private Long id;
    private Integer contentType;
    private String name;
    private Long sizeBytes;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String category;
    private String keywords;
    private LocalDateTime publishedTime;
    /** 模板示意图；非模板为 null */
    private String thumb;

}
