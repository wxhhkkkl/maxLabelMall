package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 软件版本 Response VO")
@Data
public class AdminSoftwareVersionRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "软件编号")
    private Long softwareId;
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
    @Schema(description = "sha512（Base64）")
    private String sha512;
    @Schema(description = "公开下载地址")
    private String fileUrl;
    @Schema(description = "对象键")
    private String objectKey;
    @Schema(description = "发布说明")
    private String releaseNotes;
    @Schema(description = "发布日期")
    private LocalDateTime releaseTime;
    @Schema(description = "是否当前发布")
    private Boolean current;
    @Schema(description = "状态：0 已发布 1 草稿 2 已下架")
    private Integer status;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
