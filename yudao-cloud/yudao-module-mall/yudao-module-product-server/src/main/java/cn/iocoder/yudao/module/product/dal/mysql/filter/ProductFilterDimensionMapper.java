package cn.iocoder.yudao.module.product.dal.mysql.filter;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterDimensionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 商品筛选维度 Mapper
 */
@Mapper
public interface ProductFilterDimensionMapper extends BaseMapperX<ProductFilterDimensionDO> {

    /**
     * 查询若干分类下的维度。
     *
     * ⚠️ 传进来的 `categoryIds` 应当是 **`scopeOf(categoryId)`**（分类自身 + 全部祖先），
     * 而不是单个分类 —— 「向下继承」是靠这个调用方把祖先链一起传进来实现的（Q10=B）。
     * 用 `IN` 而不是逐个查，是为了让"3 层链"这种情况仍然只走一条 SQL。
     */
    default List<ProductFilterDimensionDO> selectListByCategoryIds(Collection<Long> categoryIds) {
        return selectList(new LambdaQueryWrapperX<ProductFilterDimensionDO>()
                .in(ProductFilterDimensionDO::getCategoryId, categoryIds)
                .orderByAsc(ProductFilterDimensionDO::getSort)
                .orderByAsc(ProductFilterDimensionDO::getId));
    }

    /**
     * 查询某个分类**自身**配置的维度（不含继承来的）。
     *
     * 后台抽屉要把「本分类自有」与「继承自父分类」分两段展示（FR-110），
     * 故这里刻意不展开祖先链 —— 那是上面那个方法的事。
     */
    default List<ProductFilterDimensionDO> selectListByCategoryId(Long categoryId) {
        return selectList(new LambdaQueryWrapperX<ProductFilterDimensionDO>()
                .eq(ProductFilterDimensionDO::getCategoryId, categoryId)
                .orderByAsc(ProductFilterDimensionDO::getSort)
                .orderByAsc(ProductFilterDimensionDO::getId));
    }

    default Long selectCountByCategoryId(Long categoryId) {
        return selectCount(ProductFilterDimensionDO::getCategoryId, categoryId);
    }

}
