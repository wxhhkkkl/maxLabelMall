package cn.iocoder.yudao.module.member.controller.app.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 私有云端模板 Response VO")
@Data
public class AppLabelTemplateRespVO {

    private Long id;
    private String name;
    private Integer sizeBytes;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String category;
    private String keywords;
    /** 共享状态：null 未共享 / 0 待审核 / 1 已公开 / 2 已驳回 / 3 已下架 */
    private Integer shareStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
