package cn.iocoder.yudao.module.product.controller.app.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * C 端 - 客户端更新检查 Response VO（013 / Q89）
 *
 * 判定在服务端完成；无更新时只返回 hasUpdate=false，不带任何文件信息。
 */
@Schema(description = "用户 App - 客户端更新检查 Response VO")
@Data
public class AppSoftwareUpdateCheckRespVO {

    @Schema(description = "是否有更新")
    private Boolean hasUpdate;

    @Schema(description = "最新版本号")
    private String latestVersion;

    @Schema(description = "sha512（Base64）")
    private String sha512;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "electron-updater 的 feed 目录（其下 latest.yml 由本服务生成）")
    private String feedUrl;

    @Schema(description = "发布说明")
    private String releaseNotes;

    public static AppSoftwareUpdateCheckRespVO noUpdate() {
        AppSoftwareUpdateCheckRespVO vo = new AppSoftwareUpdateCheckRespVO();
        vo.setHasUpdate(false);
        return vo;
    }

}
