package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 软件分类 Response VO")
@Data
public class AdminSoftwareCategoryRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "名称")
    private String name;
    @Schema(description = "排序")
    private Integer sort;
    @Schema(description = "状态：0 启用 1 停用")
    private Integer status;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
