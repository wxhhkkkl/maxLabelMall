package cn.iocoder.yudao.module.member.controller.app.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户 App - 私有云端模板正文 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppLabelTemplateContentRespVO extends AppLabelTemplateRespVO {

    @Schema(description = "模板正文（.msdx JSON）")
    private String content;

}
