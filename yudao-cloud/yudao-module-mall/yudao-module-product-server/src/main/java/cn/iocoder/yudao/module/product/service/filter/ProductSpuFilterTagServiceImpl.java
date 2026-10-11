package cn.iocoder.yudao.module.product.service.filter;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterOptionRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterDimensionDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterValueDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductSpuFilterValueDO;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductFilterDimensionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductFilterValueMapper;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductSpuFilterValueMapper;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FILTER_VALUE_MULTI_IN_DIMENSION;

/**
 * {@link ProductSpuFilterTagService} 的实现。
 *
 * 本类**只做一件事**：把「商品能挂哪些维度值」这条规则落成可执行代码。
 * 它不认识 SPU 的其他字段，也不认识 SKU —— 那正是它能被独立测试的原因。
 */
@Service
@Validated
public class ProductSpuFilterTagServiceImpl implements ProductSpuFilterTagService {

    @Resource
    private ProductSpuFilterValueMapper spuFilterValueMapper;
    @Resource
    private ProductFilterDimensionMapper dimensionMapper;
    @Resource
    private ProductFilterValueMapper valueMapper;
    @Resource
    private ProductCategoryService categoryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSpuFilterValues(Long spuId, Long categoryId, List<Long> filterValueIds) {
        // 1. 算出该商品的辖域：自身 + 全部祖先（Q10=B）。
        //    运营是在「碳带」上配「宽度」的，而商品挂在叶子「蜡基」上 —— 不带上祖先链，
        //    运营配的东西就一件商品都挂不上。
        Set<Long> scope = categoryService.scopeOf(categoryId);
        List<ProductFilterDimensionDO> dimensions = dimensionMapper.selectListByCategoryIds(scope);
        Set<Long> allowedValueIds = new LinkedHashSet<>();
        if (CollUtil.isNotEmpty(dimensions)) {
            valueMapper.selectListByDimensionIds(toIds(dimensions))
                    .forEach(value -> allowedValueIds.add(value.getId()));
        }

        // 2. 全量替换：先清空该商品的全部关联，再写入留下的那些。
        //    "先清后写"是**唯一**的去重手段（表上刻意没建唯一索引，理由见建表脚本），
        //    同时它顺带实现了 FR-097 —— 改分类后旧标签会因为没被重新写入而自然消失。
        spuFilterValueMapper.deleteBySpuId(spuId);
        if (CollUtil.isEmpty(filterValueIds)) {
            return;
        }
        filterValueIds.stream()
                .distinct()
                .filter(allowedValueIds::contains) // 辖域外的静默丢弃（FR-095）
                .forEach(valueId -> spuFilterValueMapper.insert(ProductSpuFilterValueDO.builder()
                        .spuId(spuId).filterValueId(valueId).build()));
    }

    @Override
    public void validateSingleValuePerDimension(Collection<Long> filterValueIds) {
        if (CollUtil.isEmpty(filterValueIds)) {
            return;
        }
        // 查这些值分别属于哪个维度，然后看有没有哪个维度被选了两次
        Map<Long, Long> dimensionIdByValueId = valueMapper.selectListByIds(filterValueIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProductFilterValueDO::getId, ProductFilterValueDO::getDimensionId));
        Set<Long> seenDimensionIds = new java.util.HashSet<>();
        for (Long filterValueId : filterValueIds) {
            Long dimensionId = dimensionIdByValueId.get(filterValueId);
            if (dimensionId == null) {
                continue; // 值不存在/已删：交给查询去处理（查不到就是查不到），这里不报
            }
            if (!seenDimensionIds.add(dimensionId)) {
                ProductFilterDimensionDO dimension = dimensionMapper.selectById(dimensionId);
                throw exception(FILTER_VALUE_MULTI_IN_DIMENSION,
                        dimension != null ? dimension.getName() : String.valueOf(dimensionId));
            }
        }
    }

    @Override
    public List<Long> getFilterValueIdsBySpuId(Long spuId) {
        return spuFilterValueMapper.selectListBySpuId(spuId).stream()
                .map(ProductSpuFilterValueDO::getFilterValueId)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Override
    public List<ProductFilterOptionRespVO> getFilterValueOptionList(Long categoryId) {
        List<ProductFilterDimensionDO> dimensions =
                dimensionMapper.selectListByCategoryIds(categoryService.scopeOf(categoryId));
        if (CollUtil.isEmpty(dimensions)) {
            return List.of();
        }
        // 一次取完所有值，再在内存里按维度分组 —— 维度通常只有两三个，不值得为它做 N+1
        Map<Long, List<ProductFilterValueDO>> valuesByDimension = valueMapper
                .selectListByDimensionIds(toIds(dimensions)).stream()
                .collect(java.util.stream.Collectors.groupingBy(ProductFilterValueDO::getDimensionId));

        return dimensions.stream()
                .map(dimension -> {
                    List<ProductFilterValueDO> values =
                            valuesByDimension.getOrDefault(dimension.getId(), List.of());
                    if (values.isEmpty()) {
                        return null; // 没有可选值的维度不返回：渲染一个空维度没有意义（同 FR-090）
                    }
                    ProductFilterOptionRespVO vo = new ProductFilterOptionRespVO();
                    vo.setDimensionId(dimension.getId());
                    vo.setDimensionName(dimension.getName());
                    vo.setSort(dimension.getSort());
                    vo.setFromCategoryName(categoryName(dimension.getCategoryId()));
                    vo.setValues(values.stream()
                            .map(value -> new ProductFilterOptionRespVO.Value(
                                    value.getId(), value.getName(), value.getSort()))
                            .toList());
                    return vo;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String categoryName(Long categoryId) {
        ProductCategoryDO category = categoryService.getCategory(categoryId);
        return category != null ? category.getName() : String.valueOf(categoryId);
    }

    private static List<Long> toIds(List<ProductFilterDimensionDO> dimensions) {
        return dimensions.stream().map(ProductFilterDimensionDO::getId).toList();
    }

}
