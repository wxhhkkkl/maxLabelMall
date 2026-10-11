package cn.iocoder.yudao.module.product.controller.admin.filter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 筛选维度可选值 Response VO")
@Data
public class ProductFilterValueRespVO {

    @Schema(description = "编号", example = "3")
    private Long id;

    @Schema(description = "所属筛选维度编号", example = "12")
    private Long dimensionId;

    @Schema(description = "可选值名称", example = "110")
    private String name;

    @Schema(description = "展示顺序", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

}
