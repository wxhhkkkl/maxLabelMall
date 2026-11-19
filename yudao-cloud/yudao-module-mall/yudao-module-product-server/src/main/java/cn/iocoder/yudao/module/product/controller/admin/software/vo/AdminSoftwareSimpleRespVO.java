package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 软件/驱动精简 Response VO（商品「其他设置 → 关联驱动下载」用，2026-11-18）
 */
@Schema(description = "管理后台 - 软件/驱动精简 Response VO")
@Data
public class AdminSoftwareSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long id;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "zenpert4T530")
    private String title;

    @Schema(description = "类型：1 桌面端软件 2 打印驱动", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer type;

    @Schema(description = "状态：0 启用 1 停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

}
