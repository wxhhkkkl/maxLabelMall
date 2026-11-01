package cn.iocoder.yudao.module.trade.controller.app.customization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "用户 App - 定制设计文件")
@Data
public class AppCustomizationFileVO {

    @Schema(description = "文件名", requiredMode = Schema.RequiredMode.REQUIRED, example = "artwork.pdf")
    private String name;

    @Schema(description = "对象存储地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "文件地址不能为空")
    private String url;

    @Schema(description = "文件大小（字节）", example = "1048576")
    private Long size;

    @Schema(description = "MIME 类型", example = "application/pdf")
    private String type;

}
