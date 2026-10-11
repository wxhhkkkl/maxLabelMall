package cn.iocoder.yudao.module.product.service.filter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDeleteImpactRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterValueSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterDimensionDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterValueDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductSpuFilterValueDO;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductFilterDimensionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductFilterValueMapper;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductSpuFilterValueMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

/**
 * 筛选维度可选值 Service 实现类
 */
@Service
@Validated
public class ProductFilterValueServiceImpl implements ProductFilterValueService {

    @Resource
    private ProductFilterValueMapper valueMapper;
    @Resource
    private ProductFilterDimensionMapper dimensionMapper;
    @Resource
    private ProductSpuFilterValueMapper spuFilterValueMapper;

    @Override
    public Long createValue(ProductFilterValueSaveReqVO createReqVO) {
        validateDimensionExists(createReqVO.getDimensionId());
        String name = trimName(createReqVO.getName());
        validateNameNotDuplicate(createReqVO.getDimensionId(), name, null);

        ProductFilterValueDO value = new ProductFilterValueDO();
        value.setDimensionId(createReqVO.getDimensionId());
        value.setName(name);
        value.setSort(createReqVO.getSort() != null ? createReqVO.getSort() : 0);
        value.setRemark(createReqVO.getRemark());
        valueMapper.insert(value);
        return value.getId();
    }

    @Override
    public void updateValue(ProductFilterValueSaveReqVO updateReqVO) {
        ProductFilterValueDO exists = validateValueExists(updateReqVO.getId());
        String name = trimName(updateReqVO.getName());
        validateNameNotDuplicate(exists.getDimensionId(), name, updateReqVO.getId());

        ProductFilterValueDO updateObj = new ProductFilterValueDO();
        updateObj.setId(updateReqVO.getId());
        updateObj.setName(name);
        updateObj.setSort(updateReqVO.getSort() != null ? updateReqVO.getSort() : exists.getSort());
        updateObj.setRemark(updateReqVO.getRemark());
        valueMapper.updateById(updateObj);
    }

    @Override
    public void deleteValue(Long id) {
        validateValueExists(id);
        // 先清商品上的关联，再删值 —— 否则留下的是"指向不存在标签"的关联（FR-092）
        spuFilterValueMapper.deleteByFilterValueId(id);
        valueMapper.deleteById(id);
    }

    @Override
    public ProductFilterValueDO getValue(Long id) {
        return valueMapper.selectById(id);
    }

    @Override
    public List<ProductFilterValueDO> getValueListByDimensionId(Long dimensionId) {
        return valueMapper.selectListByDimensionId(dimensionId);
    }

    @Override
    public List<ProductFilterValueDO> getValueListByDimensionIds(Collection<Long> dimensionIds) {
        if (CollUtil.isEmpty(dimensionIds)) {
            return List.of();
        }
        return valueMapper.selectListByDimensionIds(dimensionIds);
    }

    @Override
    public ProductFilterDeleteImpactRespVO getDeleteImpact(Long id) {
        validateValueExists(id);
        long spuCount = spuFilterValueMapper.selectListByFilterValueId(id).stream()
                .map(ProductSpuFilterValueDO::getSpuId)
                .distinct()
                .count();
        return new ProductFilterDeleteImpactRespVO(0L, spuCount);
    }

    // ==================== 私有 ====================

    private ProductFilterValueDO validateValueExists(Long id) {
        ProductFilterValueDO value = valueMapper.selectById(id);
        if (value == null) {
            throw exception(FILTER_VALUE_NOT_EXISTS);
        }
        return value;
    }

    private void validateDimensionExists(Long dimensionId) {
        if (dimensionMapper.selectById(dimensionId) == null) {
            throw exception(FILTER_DIMENSION_NOT_EXISTS);
        }
    }

    private String trimName(String name) {
        String trimmed = StrUtil.trim(name);
        if (StrUtil.isEmpty(trimmed)) {
            throw exception(FILTER_VALUE_NAME_EMPTY);
        }
        return trimmed;
    }

    /** 重名只在**同一个维度内**禁；不同维度下可以同名（碳带下的「110」与别处的「110」互不相干） */
    private void validateNameNotDuplicate(Long dimensionId, String name, Long excludeId) {
        for (ProductFilterValueDO value : valueMapper.selectListByDimensionId(dimensionId)) {
            if (Objects.equals(value.getId(), excludeId)) {
                continue;
            }
            if (Objects.equals(value.getName(), name)) {
                // 报错里带上维度名，运营一眼知道是哪个维度下的冲突
                String dimensionName = java.util.Optional.ofNullable(dimensionMapper.selectById(dimensionId))
                        .map(ProductFilterDimensionDO::getName).orElse(String.valueOf(dimensionId));
                throw exception(FILTER_VALUE_NAME_DUPLICATE, dimensionName, name);
            }
        }
    }

}
