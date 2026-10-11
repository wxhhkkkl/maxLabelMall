package cn.iocoder.yudao.module.product.controller.admin.filter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 某个分类的筛选维度配置，**分两段**返回。
 *
 * 为什么不合成一个列表：两段的**可操作性不同** —— `own` 能增删改，
 * `inherited` 改了会影响整棵子树。合成一个列表会让后台渲染出"看起来能点、其实不该点"的项。
 */
@Schema(description = "管理后台 - 分类的筛选维度配置 Response VO")
@Data
public class ProductFilterDimensionListRespVO {

    @Schema(description = "本分类自有的维度（可增删改）")
    private List<ProductFilterDimensionRespVO> own;

    @Schema(description = "继承自祖先分类的维度（只读）")
    private List<ProductFilterDimensionRespVO> inherited;

}
