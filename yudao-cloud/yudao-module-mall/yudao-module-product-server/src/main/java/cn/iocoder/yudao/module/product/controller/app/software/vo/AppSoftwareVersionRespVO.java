package cn.iocoder.yudao.module.product.controller.app.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 软件版本 Response VO（只含当前发布）")
@Data
public class AppSoftwareVersionRespVO {

    @Schema(description = "版本号")
    private String version;
    @Schema(description = "平台")
    private String platform;
    @Schema(description = "架构")
    private String arch;
    @Schema(description = "文件名")
    private String fileName;
    @Schema(description = "文件大小（字节）")
    private Long fileSize;
    @Schema(description = "发布日期")
    private LocalDateTime releaseTime;
    @Schema(description = "公开永久下载直链")
    private String downloadUrl;

}
