package cn.iocoder.yudao.module.product.dal.mysql.filter;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductSpuFilterValueDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 商品与筛选可选值的关联 Mapper
 *
 * ⚠️ **筛选查询用的 `EXISTS` 不在这里** —— 它在
 * `ProductSpuMapper.selectPage(...)` 里通过 `LambdaQueryWrapperX.exists(...)` 追加，
 * 为的是不重写既有的分页与排序（见 `research.md` R5 的被否决方案）。
 * 这个 Mapper 只负责"读回来 / 清掉 / 数引用"这三件事。
 */
@Mapper
public interface ProductSpuFilterValueMapper extends BaseMapperX<ProductSpuFilterValueDO> {

    /**
     * 查询一个商品挂着的全部关联（商品编辑页读回用）。
     */
    default List<ProductSpuFilterValueDO> selectListBySpuId(Long spuId) {
        return selectList(ProductSpuFilterValueDO::getSpuId, spuId);
    }

    /**
     * 查询若干商品挂着的关联（批量，避免 N+1）。
     */
    default List<ProductSpuFilterValueDO> selectListBySpuIds(Collection<Long> spuIds) {
        return selectList(new LambdaQueryWrapperX<ProductSpuFilterValueDO>()
                .in(ProductSpuFilterValueDO::getSpuId, spuIds));
    }

    /**
     * 查询一个可选值被哪些商品挂着（删除前的影响告知，FR-092）。
     *
     * 返回的是**关联行**，调用方取 `spuId` 去重后即为受影响商品数。
     */
    default List<ProductSpuFilterValueDO> selectListByFilterValueId(Long filterValueId) {
        return selectList(ProductSpuFilterValueDO::getFilterValueId, filterValueId);
    }

    default List<ProductSpuFilterValueDO> selectListByFilterValueIds(Collection<Long> filterValueIds) {
        return selectList(new LambdaQueryWrapperX<ProductSpuFilterValueDO>()
                .in(ProductSpuFilterValueDO::getFilterValueId, filterValueIds));
    }

    /**
     * 删除一个商品的全部关联（保存时"先清后写"的第一步 —— 这是去重的唯一手段，
     * 因为表上刻意没建唯一索引，理由见建表脚本与 DO 注释）。
     */
    default void deleteBySpuId(Long spuId) {
        delete(new LambdaQueryWrapperX<ProductSpuFilterValueDO>()
                .eq(ProductSpuFilterValueDO::getSpuId, spuId));
    }

    /**
     * 删除某个可选值上的全部关联（删除可选值时的级联清理，FR-092）。
     */
    default void deleteByFilterValueId(Long filterValueId) {
        delete(new LambdaQueryWrapperX<ProductSpuFilterValueDO>()
                .eq(ProductSpuFilterValueDO::getFilterValueId, filterValueId));
    }

    /**
     * 删除某个维度下全部可选值上的关联（删除维度时的级联清理）。
     *
     * 调用方需先查出该维度下的 `valueIds`。
     */
    default void deleteByFilterValueIds(Collection<Long> filterValueIds) {
        if (filterValueIds == null || filterValueIds.isEmpty()) {
            return;
        }
        delete(new LambdaQueryWrapperX<ProductSpuFilterValueDO>()
                .in(ProductSpuFilterValueDO::getFilterValueId, filterValueIds));
    }

}
