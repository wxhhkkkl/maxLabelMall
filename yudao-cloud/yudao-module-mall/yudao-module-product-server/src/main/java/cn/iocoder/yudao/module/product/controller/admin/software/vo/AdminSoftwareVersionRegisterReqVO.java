package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 软件版本登记 Request VO")
@Data
public class AdminSoftwareVersionRegisterReqVO {

    @Schema(description = "软件编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "软件编号不能为空")
    private Long softwareId;

    @Schema(description = "版本号（x.y.z）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "版本号不能为空")
    private String version;

    @Schema(description = "平台：windows / macos / linux", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "平台不能为空")
    private String platform;

    @Schema(description = "架构：x64 / arm64 / x86", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "架构不能为空")
    private String arch;

    @Schema(description = "原始文件名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255)
    private String fileName;

    @Schema(description = "文件大小（字节）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    @Schema(description = "Base64 编码的 sha512", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "校验和不能为空")
    private String sha512;

    @Schema(description = "对象键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "对象键不能为空")
    private String objectKey;

    @Schema(description = "公开下载地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "下载地址不能为空")
    private String fileUrl;

    @Schema(description = "发布说明")
    @Size(max = 2000)
    private String releaseNotes;

    @Schema(description = "发布日期")
    private LocalDateTime releaseTime;

}
