package cn.iocoder.yudao.module.product.controller.app.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 软件 Response VO")
@Data
public class AppSoftwareRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "分类编号")
    private Long categoryId;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "介绍")
    private String introduction;
    @Schema(description = "类型：1 桌面端软件 2 打印驱动")
    private Integer type;
    @Schema(description = "当前发布版本号（可能为空）")
    private String latestVersion;
    @Schema(description = "有当前发布的平台")
    private List<String> platforms;

}
