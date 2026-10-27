package cn.iocoder.yudao.module.product.controller.app.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 软件分类 Response VO")
@Data
public class AppSoftwareCategoryRespVO {

    @Schema(description = "分类编号")
    private Long id;
    @Schema(description = "分类名称")
    private String name;
    @Schema(description = "排序")
    private Integer sort;

}
