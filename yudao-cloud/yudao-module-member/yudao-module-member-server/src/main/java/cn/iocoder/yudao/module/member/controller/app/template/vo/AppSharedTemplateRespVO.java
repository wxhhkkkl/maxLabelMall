package cn.iocoder.yudao.module.member.controller.app.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 平台共享模板 Response VO")
@Data
public class AppSharedTemplateRespVO {

    private Long id;
    private String name;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String category;
    private String keywords;
    private String publisherNickname;
    private LocalDateTime publishedTime;
    /** 示意图（SVG data URL，由正文即时生成）。 */
    private String thumb;

}
