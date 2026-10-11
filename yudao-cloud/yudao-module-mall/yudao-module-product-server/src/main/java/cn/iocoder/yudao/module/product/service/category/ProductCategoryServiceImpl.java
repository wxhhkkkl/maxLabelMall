package cn.iocoder.yudao.module.product.service.category;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO.CATEGORY_LEVEL;
import static cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO.PARENT_ID_NULL;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

/**
 * 商品分类 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class ProductCategoryServiceImpl implements ProductCategoryService {

    @Resource
    private ProductCategoryMapper productCategoryMapper;
    @Resource
    @Lazy // 循环依赖，避免报错
    private ProductSpuService productSpuService;

    @Override
    public Long createCategory(ProductCategorySaveReqVO createReqVO) {
        // 校验父分类存在
        validateParentProductCategory(createReqVO.getParentId());

        // 插入
        ProductCategoryDO category = BeanUtils.toBean(createReqVO, ProductCategoryDO.class);
        productCategoryMapper.insert(category);
        // 返回
        return category.getId();
    }

    @Override
    public void updateCategory(ProductCategorySaveReqVO updateReqVO) {
        // 校验分类是否存在
        validateProductCategoryExists(updateReqVO.getId());
        // 校验父分类存在
        validateParentProductCategory(updateReqVO.getParentId());

        // 更新
        ProductCategoryDO updateObj = BeanUtils.toBean(updateReqVO, ProductCategoryDO.class);
        productCategoryMapper.updateById(updateObj);
    }

    @Override
    public void deleteCategory(Long id) {
        // 校验分类是否存在
        validateProductCategoryExists(id);
        // 校验是否还有子分类
        if (productCategoryMapper.selectCountByParentId(id) > 0) {
            throw exception(CATEGORY_EXISTS_CHILDREN);
        }
        // 校验分类是否绑定了 SPU
        Long spuCount = productSpuService.getSpuCountByCategoryId(id);
        if (spuCount > 0) {
            throw exception(CATEGORY_HAVE_BIND_SPU);
        }
        // 删除
        productCategoryMapper.deleteById(id);
    }

    private void validateParentProductCategory(Long id) {
        // 如果是根分类，无需验证
        if (Objects.equals(id, PARENT_ID_NULL)) {
            return;
        }
        // 父分类不存在
        ProductCategoryDO category = productCategoryMapper.selectById(id);
        if (category == null) {
            throw exception(CATEGORY_PARENT_NOT_EXISTS);
        }
        // 父分类不能是二级分类
        if (!Objects.equals(category.getParentId(), PARENT_ID_NULL)) {
            throw exception(CATEGORY_PARENT_NOT_FIRST_LEVEL);
        }
    }

    private void validateProductCategoryExists(Long id) {
        ProductCategoryDO category = productCategoryMapper.selectById(id);
        if (category == null) {
            throw exception(CATEGORY_NOT_EXISTS);
        }
    }

    @Override
    public void validateCategoryList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 获得商品分类信息
        List<ProductCategoryDO> list = productCategoryMapper.selectByIds(ids);
        Map<Long, ProductCategoryDO> categoryMap = CollectionUtils.convertMap(list, ProductCategoryDO::getId);
        // 校验
        ids.forEach(id -> {
            // 校验分类是否存在
            ProductCategoryDO category = categoryMap.get(id);
            if (category == null) {
                throw exception(CATEGORY_NOT_EXISTS);
            }
            // 校验分类是否启用
            if (!CommonStatusEnum.ENABLE.getStatus().equals(category.getStatus())) {
                throw exception(CATEGORY_DISABLED, category.getName());
            }
            // 商品分类层级校验，必须使用第二级的商品分类
            if (getCategoryLevel(id) < CATEGORY_LEVEL) {
                throw exception(SPU_SAVE_FAIL_CATEGORY_LEVEL_ERROR);
            }
        });
    }

    @Override
    public ProductCategoryDO getCategory(Long id) {
        return productCategoryMapper.selectById(id);
    }

    @Override
    public void validateCategory(Long id) {
        ProductCategoryDO category = productCategoryMapper.selectById(id);
        if (category == null) {
            throw exception(CATEGORY_NOT_EXISTS);
        }
        if (Objects.equals(category.getStatus(), CommonStatusEnum.DISABLE.getStatus())) {
            throw exception(CATEGORY_DISABLED, category.getName());
        }
    }

    @Override
    public Integer getCategoryLevel(Long id) {
        if (Objects.equals(id, PARENT_ID_NULL)) {
            return 0;
        }
        int level = 1;
        // for 的原因，是因为避免脏数据，导致可能的死循环。一般不会超过 100 层哈
        for (int i = 0; i < Byte.MAX_VALUE; i++) {
            // 如果没有父节点，break 结束
            ProductCategoryDO category = productCategoryMapper.selectById(id);
            if (category == null
                    || Objects.equals(category.getParentId(), PARENT_ID_NULL)) {
                break;
            }
            // 继续递归父节点
            level++;
            id = category.getParentId();
        }
        return level;
    }

    /**
     * 向上走链的深度上限。
     *
     * 线上分类树只有 3 层（2026-10-11 实测 42 个分类），这里给到 32 已经**远超**真实需要，
     * 纯粹是"数据脏了也别把服务挂死"的兜底 —— 与上面 {@link #getCategoryLevel(Long)}
     * 用 `Byte.MAX_VALUE` 兜底是同一种考虑。
     */
    private static final int SCOPE_MAX_DEPTH = 32;

    @Override
    public Set<Long> scopeOf(Long categoryId) {
        Set<Long> scope = new LinkedHashSet<>();
        Long current = categoryId;
        // 两道防线防"数据脏了导致 parentId 成环"：深度上限 + 下一跳是否已经走过。
        // 任一生效都让循环停下来，绝不把服务挂死。
        for (int i = 0; i < SCOPE_MAX_DEPTH && !Objects.equals(current, PARENT_ID_NULL); i++) {
            ProductCategoryDO category = productCategoryMapper.selectById(current);
            if (category == null) {
                break; // 不存在 / 链断在半路：走到哪算哪，只收已经确认真实存在的那部分
            }
            scope.add(category.getId());
            Long parentId = category.getParentId();
            if (parentId == null || Objects.equals(parentId, PARENT_ID_NULL)) {
                break; // 到根了
            }
            if (scope.contains(parentId)) {
                break; // 成环
            }
            current = parentId;
        }
        // ⚠️ 起点本身不存在时，**仍然**把它返回，而不是返回空集。
        //    因为调用方普遍写成 `inIfPresent(category_id, scope)`，而那个方法在集合为空时
        //    **会跳过整个条件** —— 那就从"查不到维度"变成"查出所有维度"，是个静默的放大。
        //    返回 {categoryId} 语义上也对：问的就是这个分类，它没有维度就是没有。
        if (scope.isEmpty()) {
            scope.add(categoryId);
        }
        return scope;
    }

    @Override
    public List<ProductCategoryDO> getCategoryList(ProductCategoryListReqVO listReqVO) {
        return productCategoryMapper.selectList(listReqVO);
    }

    @Override
    public List<ProductCategoryDO> getEnableCategoryList() {
        return productCategoryMapper.selectListByStatus(CommonStatusEnum.ENABLE.getStatus());
    }

    @Override
    public List<ProductCategoryDO> getEnableCategoryList(List<Long> ids) {
        return productCategoryMapper.selectListByIdAndStatus(ids, CommonStatusEnum.ENABLE.getStatus());
    }

}
