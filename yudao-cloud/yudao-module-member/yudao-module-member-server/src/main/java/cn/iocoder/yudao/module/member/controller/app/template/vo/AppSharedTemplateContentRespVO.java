package cn.iocoder.yudao.module.member.controller.app.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户 App - 平台共享模板正文 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppSharedTemplateContentRespVO extends AppSharedTemplateRespVO {

    @Schema(description = "清洗后的模板正文")
    private String content;

}
