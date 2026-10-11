package cn.iocoder.yudao.module.product.dal.dataobject.filter;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 商品筛选维度可选值 DO
 *
 * 属于某个 {@link ProductFilterDimensionDO}，例如「宽度」下的「110」。
 * **前台能点的就是它。**
 *
 * ⚠️ 它当前**有没有对应的商品**，与它**展不展示**无关 —— 选项集合只由后台配置决定
 * （决策 Q11=A，规格 FR-111）。这条不是细节：Q11 曾在 B（按商品数动态收敛）上走过一个来回，
 * 最终回到 A，理由与后果留档在 `research.md` R6。
 */
@TableName("product_filter_value")
@KeySequence("product_filter_value_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterValueDO extends BaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 所属筛选维度编号
     *
     * 关联 {@link ProductFilterDimensionDO#getId()}
     */
    private Long dimensionId;
    /**
     * 可选值名称，如「110」
     */
    private String name;
    /**
     * 展示顺序
     */
    private Integer sort;
    /**
     * 备注
     */
    private String remark;

}
