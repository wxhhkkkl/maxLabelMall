package cn.iocoder.yudao.module.product.dal.dataobject.filter;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 商品筛选维度 DO
 *
 * 挂在**某个商品分类**下的一组筛选条件，例如「碳带」下的「宽度」。
 *
 * ⚠️ 它**只存单一归属** {@link #categoryId}；「向下继承到子分类」不是存储结构，
 * 而是查询时按**分类祖先链**实时求出来的（决策 Q10=B，见
 * `specs/003-product-filter-dimensions/spec.md`）。因此这里不需要任何"展开表"。
 *
 * ⚠️ **不要**把它和 `product_property` 混起来：那是**全局**的、服务于 SKU 规格的祖传机制
 * （见 `research.md` R1 的核查结论）。两者语义不同，故各自独立建模。
 *
 * @see ProductFilterValueDO
 */
@TableName("product_filter_dimension")
@KeySequence("product_filter_dimension_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterDimensionDO extends BaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 归属的商品分类编号
     *
     * 关联 {@link cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO#getId()}
     */
    private Long categoryId;
    /**
     * 维度名称，如「宽度」
     */
    private String name;
    /**
     * 展示顺序
     *
     * 前台**只按它排**，不得按任何运行时数据（例如商品数）重排 —— 见规格 FR-112。
     */
    private Integer sort;
    /**
     * 备注
     */
    private String remark;

}
