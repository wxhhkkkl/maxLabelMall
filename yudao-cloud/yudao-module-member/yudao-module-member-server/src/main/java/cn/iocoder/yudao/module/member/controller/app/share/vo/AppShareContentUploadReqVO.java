package cn.iocoder.yudao.module.member.controller.app.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Schema(description = "用户 App - 共享内容上传 Request VO")
@Data
public class AppShareContentUploadReqVO {

    @Schema(description = "空间类型：1 个人 2 企业", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer scope;

    @Schema(description = "文件夹编号；企业空间必填")
    private Long folderId;

    @Schema(description = "内容类型：1 模板 2 txt 3 excel 4 csv 5 图片", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer contentType;

    @Schema(description = "名称（含扩展名）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "名称不能为空")
    @Size(max = 255, message = "名称不能超过 255 个字符")
    private String name;

    @Schema(description = "文件附件", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件附件不能为空")
    private MultipartFile file;

    private BigDecimal widthMm;
    private BigDecimal heightMm;
    @Size(max = 64, message = "分类不能超过 64 个字符")
    private String category;
    @Size(max = 255, message = "关键词不能超过 255 个字符")
    private String keywords;

}
