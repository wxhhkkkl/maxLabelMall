package cn.iocoder.yudao.module.product.dal.mysql.filter;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterValueDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 商品筛选维度可选值 Mapper
 */
@Mapper
public interface ProductFilterValueMapper extends BaseMapperX<ProductFilterValueDO> {

    /**
     * 查询一个维度下的全部可选值，按运营设定的顺序返回。
     *
     * ⚠️ **顺序只认 `sort`**，不按商品数、不按任何运行时数据重排（FR-112）。
     */
    default List<ProductFilterValueDO> selectListByDimensionId(Long dimensionId) {
        return selectList(new LambdaQueryWrapperX<ProductFilterValueDO>()
                .eq(ProductFilterValueDO::getDimensionId, dimensionId)
                .orderByAsc(ProductFilterValueDO::getSort)
                .orderByAsc(ProductFilterValueDO::getId));
    }

    /**
     * 批量查询若干维度下的可选值（前台渲染筛选条时用，一次取完避免 N+1）。
     */
    default List<ProductFilterValueDO> selectListByDimensionIds(Collection<Long> dimensionIds) {
        return selectList(new LambdaQueryWrapperX<ProductFilterValueDO>()
                .in(ProductFilterValueDO::getDimensionId, dimensionIds)
                .orderByAsc(ProductFilterValueDO::getSort)
                .orderByAsc(ProductFilterValueDO::getId));
    }

    default List<ProductFilterValueDO> selectListByIds(Collection<Long> ids) {
        return selectList(ProductFilterValueDO::getId, ids);
    }

    default Long selectCountByDimensionId(Long dimensionId) {
        return selectCount(ProductFilterValueDO::getDimensionId, dimensionId);
    }

    default List<ProductFilterValueDO> selectListByDimensionIdAndName(Long dimensionId, String name) {
        return selectList(new LambdaQueryWrapperX<ProductFilterValueDO>()
                .eq(ProductFilterValueDO::getDimensionId, dimensionId)
                .eq(ProductFilterValueDO::getName, name));
    }

}
