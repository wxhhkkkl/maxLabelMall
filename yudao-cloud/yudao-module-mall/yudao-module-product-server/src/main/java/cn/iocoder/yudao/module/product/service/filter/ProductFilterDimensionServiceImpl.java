package cn.iocoder.yudao.module.product.service.filter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDeleteImpactRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDimensionSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterDimensionDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterValueDO;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductFilterDimensionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductFilterValueMapper;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductSpuFilterValueMapper;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

/**
 * 商品筛选维度 Service 实现类
 */
@Service
@Validated
public class ProductFilterDimensionServiceImpl implements ProductFilterDimensionService {

    @Resource
    private ProductFilterDimensionMapper dimensionMapper;
    @Resource
    private ProductFilterValueMapper valueMapper;
    @Resource
    private ProductSpuFilterValueMapper spuFilterValueMapper;
    @Resource
    private ProductCategoryService categoryService;

    @Override
    public Long createDimension(ProductFilterDimensionSaveReqVO createReqVO) {
        validateCategoryExists(createReqVO.getCategoryId());
        String name = trimName(createReqVO.getName());
        // ⚠️ 重名检测查的是**整条分类祖先链**，不是只查同一个 categoryId
        validateNameNotDuplicate(createReqVO.getCategoryId(), name, null);

        ProductFilterDimensionDO dimension = BeanUtils.toBean(createReqVO, ProductFilterDimensionDO.class);
        dimension.setName(name);
        if (dimension.getSort() == null) {
            dimension.setSort(0);
        }
        dimensionMapper.insert(dimension);
        return dimension.getId();
    }

    @Override
    public void updateDimension(ProductFilterDimensionSaveReqVO updateReqVO) {
        ProductFilterDimensionDO exists = validateDimensionExists(updateReqVO.getId());
        String name = trimName(updateReqVO.getName());
        // 用**库里那条记录**的分类做检测：本接口不接受改分类（挪分类 = 换掉整条继承关系）
        validateNameNotDuplicate(exists.getCategoryId(), name, updateReqVO.getId());

        ProductFilterDimensionDO updateObj = new ProductFilterDimensionDO();
        updateObj.setId(updateReqVO.getId());
        updateObj.setName(name);
        updateObj.setSort(updateReqVO.getSort() != null ? updateReqVO.getSort() : exists.getSort());
        updateObj.setRemark(updateReqVO.getRemark());
        dimensionMapper.updateById(updateObj);
    }

    @Override
    public void deleteDimension(Long id) {
        validateDimensionExists(id);
        // 先解掉商品上对"本维度下所有可选值"的关联 —— 否则会留下指向已删除项的关联（FR-092）
        List<Long> valueIds = toValueIds(valueMapper.selectListByDimensionId(id));
        spuFilterValueMapper.deleteByFilterValueIds(valueIds);
        // 再删可选值本身，最后删维度
        valueIds.forEach(valueMapper::deleteById);
        dimensionMapper.deleteById(id);
    }

    @Override
    public void deleteDimensionList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        ids.forEach(this::deleteDimension);
    }

    @Override
    public ProductFilterDimensionDO getDimension(Long id) {
        return dimensionMapper.selectById(id);
    }

    @Override
    public List<ProductFilterDimensionDO> getDimensionListByCategoryId(Long categoryId) {
        return dimensionMapper.selectListByCategoryId(categoryId);
    }

    @Override
    public List<ProductFilterDimensionDO> getEffectiveDimensionList(Long categoryId) {
        Set<Long> scope = categoryService.scopeOf(categoryId);
        return dimensionMapper.selectListByCategoryIds(scope);
    }

    @Override
    public ProductFilterDeleteImpactRespVO getDeleteImpact(Long id) {
        validateDimensionExists(id);
        List<Long> valueIds = toValueIds(valueMapper.selectListByDimensionId(id));
        long spuCount = countDistinctSpu(valueIds);
        return new ProductFilterDeleteImpactRespVO((long) valueIds.size(), spuCount);
    }

    // ==================== 私有 ====================

    private ProductFilterDimensionDO validateDimensionExists(Long id) {
        ProductFilterDimensionDO dimension = dimensionMapper.selectById(id);
        if (dimension == null) {
            throw exception(FILTER_DIMENSION_NOT_EXISTS);
        }
        return dimension;
    }

    private void validateCategoryExists(Long categoryId) {
        ProductCategoryDO category = categoryService.getCategory(categoryId);
        if (category == null) {
            throw exception(CATEGORY_NOT_EXISTS);
        }
    }

    private String trimName(String name) {
        String trimmed = StrUtil.trim(name);
        if (StrUtil.isEmpty(trimmed)) {
            // VO 上的 @NotEmpty 拦不住 " " 这种"看着有值、去掉空白就没了"的输入
            throw exception(FILTER_DIMENSION_NAME_EMPTY);
        }
        return trimmed;
    }

    /**
     * 重名检测：在**该分类 + 它的全部祖先分类**上找同名维度。
     *
     * ⚠️ 这是本特性最容易写错的一处。只查 `category_id == 自己` 的实现，
     * 会漏掉"父分类已有「宽度」、子分类又建一个「宽度」"这种情况 ——
     * 后果是子分类页渲染出两行「宽度」，顾客无从选择。见 spec FR-091 与 tasks.md T258。
     *
     * 错误信息里 MUST 带上**冲突维度所在的分类名**，否则运营拿着"维度【宽度】已存在"
     * 不知道该去哪改 —— 它可能在任何一个祖先分类上。
     */
    private void validateNameNotDuplicate(Long categoryId, String name, Long excludeId) {
        List<ProductFilterDimensionDO> inScope =
                dimensionMapper.selectListByCategoryIds(categoryService.scopeOf(categoryId));
        for (ProductFilterDimensionDO dimension : inScope) {
            if (Objects.equals(dimension.getId(), excludeId)) {
                continue;
            }
            if (Objects.equals(dimension.getName(), name)) {
                ProductCategoryDO conflictCategory = categoryService.getCategory(dimension.getCategoryId());
                String categoryName = conflictCategory != null ? conflictCategory.getName() : String.valueOf(dimension.getCategoryId());
                throw exception(FILTER_DIMENSION_NAME_DUPLICATE, categoryName, name);
            }
        }
    }

    private static List<Long> toValueIds(List<ProductFilterValueDO> values) {
        return values.stream().map(ProductFilterValueDO::getId).toList();
    }

    /** 有多少**不同**的商品挂着这批可选值里的任意一个 */
    private long countDistinctSpu(Collection<Long> valueIds) {
        if (CollUtil.isEmpty(valueIds)) {
            return 0L;
        }
        return spuFilterValueMapper.selectListByFilterValueIds(valueIds).stream()
                .map(cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductSpuFilterValueDO::getSpuId)
                .distinct()
                .count();
    }

}
