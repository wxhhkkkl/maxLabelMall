package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 软件 Response VO")
@Data
public class AdminSoftwareRespVO {

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
    @Schema(description = "是否在服务支持页展示")
    private Boolean showInSupport;
    @Schema(description = "状态：0 启用 1 停用")
    private Integer status;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
