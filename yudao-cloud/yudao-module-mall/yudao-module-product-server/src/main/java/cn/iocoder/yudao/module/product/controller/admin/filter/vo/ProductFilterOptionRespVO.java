package cn.iocoder.yudao.module.product.controller.admin.filter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 一件商品**可挂**的筛选维度与值（契约见 contracts/admin-api.md §3.2）。
 *
 * 范围由**服务端**按商品的分类算出来（该分类自身 + 全部祖先分类，Q10=B），
 * 前端只负责渲染 —— 宪法原则 III：这类业务规则不该由前端裁决。
 */
@Schema(description = "管理后台 - 商品可挂的筛选维度与值 Response VO")
@Data
public class ProductFilterOptionRespVO {

    @Schema(description = "筛选维度编号", example = "12")
    private Long dimensionId;

    @Schema(description = "维度名称", example = "宽度")
    private String dimensionName;

    @Schema(description = "展示顺序", example = "1")
    private Integer sort;

    /**
     * 这个维度配在哪个分类上。前台/后台都要显示成「（来自「碳带」）」——
     * 不标来源的话，运营不知道这条为什么出现在这里、改它会影响谁（FR-110）。
     */
    @Schema(description = "维度所属的分类名", example = "碳带")
    private String fromCategoryName;

    @Schema(description = "可选值")
    private List<Value> values;

    @Schema(description = "可选值")
    @Data
    public static class Value {

        @Schema(description = "可选值编号", example = "3")
        private Long valueId;

        @Schema(description = "可选值名称", example = "110")
        private String name;

        @Schema(description = "展示顺序", example = "1")
        private Integer sort;

        public Value() {
        }

        public Value(Long valueId, String name, Integer sort) {
            this.valueId = valueId;
            this.name = name;
            this.sort = sort;
        }
    }

}
