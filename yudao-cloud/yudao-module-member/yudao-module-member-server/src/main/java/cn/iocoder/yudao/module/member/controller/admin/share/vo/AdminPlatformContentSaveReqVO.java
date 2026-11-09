package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 平台共享内容保存 Request VO")
@Data
public class AdminPlatformContentSaveReqVO {

    private Long id;

    @Schema(description = "内容类型：1 模板 2 txt 4 csv", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer contentType;

    @NotBlank(message = "名称不能为空")
    @Size(max = 255, message = "名称不能超过 255 个字符")
    private String name;

    private String content;

    private BigDecimal widthMm;
    private BigDecimal heightMm;
    @Size(max = 64, message = "分类不能超过 64 个字符")
    private String category;
    @Size(max = 255, message = "关键词不能超过 255 个字符")
    private String keywords;

}
