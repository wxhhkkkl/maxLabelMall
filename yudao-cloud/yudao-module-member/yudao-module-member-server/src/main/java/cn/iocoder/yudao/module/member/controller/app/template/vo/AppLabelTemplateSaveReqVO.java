package cn.iocoder.yudao.module.member.controller.app.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "用户 App - 保存私有云端模板 Request VO")
@Data
public class AppLabelTemplateSaveReqVO {

    @Schema(description = "编号（更新时必填）", example = "9001")
    private Long id;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 255, message = "模板名称不能超过 255 个字符")
    private String name;

    @Schema(description = "模板正文（.msdx JSON）；创建必填，更新可省略", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "标签宽度（毫米）")
    private BigDecimal widthMm;

    @Schema(description = "标签高度（毫米）")
    private BigDecimal heightMm;

    @Schema(description = "分类")
    @Size(max = 64, message = "分类不能超过 64 个字符")
    private String category;

    @Schema(description = "关键词")
    @Size(max = 255, message = "关键词不能超过 255 个字符")
    private String keywords;

}
