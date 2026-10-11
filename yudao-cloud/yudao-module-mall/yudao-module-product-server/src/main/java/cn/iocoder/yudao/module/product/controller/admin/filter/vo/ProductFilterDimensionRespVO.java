package cn.iocoder.yudao.module.product.controller.admin.filter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 商品筛选维度 Response VO")
@Data
public class ProductFilterDimensionRespVO {

    @Schema(description = "编号", example = "12")
    private Long id;

    @Schema(description = "归属的商品分类编号", example = "113")
    private Long categoryId;

    @Schema(description = "维度名称", example = "宽度")
    private String name;

    @Schema(description = "展示顺序", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    /**
     * 仅「继承来的维度」有值：它挂在哪个祖先分类上。
     *
     * 后台抽屉要把两段分开显示，且继承那段**只读**并把来源说清楚（FR-110）——
     * 不显示来源的话，运营改了父分类的维度，子分类页会静默变化，无从解释。
     */
    @Schema(description = "继承自哪个分类（仅继承项有值）", example = "碳带")
    private String inheritedFromCategoryName;

}
