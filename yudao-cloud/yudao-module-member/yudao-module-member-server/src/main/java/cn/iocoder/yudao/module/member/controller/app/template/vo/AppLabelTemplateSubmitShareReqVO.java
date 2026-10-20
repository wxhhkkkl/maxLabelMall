package cn.iocoder.yudao.module.member.controller.app.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 提交共享模板 Request VO")
@Data
public class AppLabelTemplateSubmitShareReqVO {

    @Schema(description = "私有模板编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模板编号不能为空")
    private Long id;

    @Schema(description = "分类")
    @Size(max = 64, message = "分类不能超过 64 个字符")
    private String category;

    @Schema(description = "关键词")
    @Size(max = 255, message = "关键词不能超过 255 个字符")
    private String keywords;

}
