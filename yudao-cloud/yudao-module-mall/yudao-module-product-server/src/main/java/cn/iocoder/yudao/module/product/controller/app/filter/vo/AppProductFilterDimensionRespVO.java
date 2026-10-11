package cn.iocoder.yudao.module.product.controller.app.filter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 用户 App - 商城页筛选条的一行（维度 + 可选值）。
 *
 * 契约见 `specs/003-product-filter-dimensions/contracts/app-api.md` §2。
 *
 * ⚠️ **没有计数、也没有"有没有商品"** —— 这是决策 Q11=A 的直接结果：
 * 选项集合**只由后台配置决定**，不随关键词或已选值收敛（FR-111）。
 * 某个值当前一件商品都没有，它**照样在这里**，顾客点选后走空态。
 *
 * ⚠️ 接口**不承诺数组顺序**，前端 MUST 按 `sort` 自行排序（FR-112）。
 */
@Schema(description = "用户 App - 商品筛选维度与可选值 Response VO")
@Data
public class AppProductFilterDimensionRespVO {

    @Schema(description = "筛选维度编号", example = "12")
    private Long dimensionId;

    @Schema(description = "维度名称", example = "宽度")
    private String dimensionName;

    @Schema(description = "展示顺序", example = "1")
    private Integer sort;

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

    }

}
