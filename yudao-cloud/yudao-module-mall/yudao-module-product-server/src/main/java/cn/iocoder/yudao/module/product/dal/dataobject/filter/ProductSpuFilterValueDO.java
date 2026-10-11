package cn.iocoder.yudao.module.product.dal.dataobject.filter;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 商品与筛选可选值的关联 DO（多对多）
 *
 * 前台筛选的**唯一依据**：顾客点一个值，服务端就是按这张表把商品筛出来的。
 *
 * ⚠️ **商品侧是多选，前台是单选，这两件事不要混**：
 * 商品可以同时挂着「宽度=110」和「宽度=100」（运营要表达的正是"这件货有这两个规格"），
 * 而顾客在前台**每一行只能选一个**（决策 Q9=B）。所以查询侧是"每个已选维度一条**等值**
 * EXISTS"，而不是"同维度取并集"。
 *
 * ⚠️ **没有 (spu_id, filter_value_id) 唯一索引**：本表继承 BaseDO，删除是**逻辑删除**，
 * 唯一索引与它天然冲突（同一个商品把某个值取消再挂回去，旧行还在，唯一约束会直接报错）。
 * 去重由服务层保证 —— 保存商品时**先清理该商品的全部关联再写入**。
 * 这是实现期发现并写下来的取舍，见建表脚本里的同段注释。
 */
@TableName("product_spu_filter_value")
@KeySequence("product_spu_filter_value_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpuFilterValueDO extends BaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 商品 SPU 编号
     *
     * 关联 {@link cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO#getId()}
     */
    private Long spuId;
    /**
     * 筛选可选值编号
     *
     * 关联 {@link ProductFilterValueDO#getId()}
     */
    private Long filterValueId;

}
