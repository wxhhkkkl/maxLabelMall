package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareCategoryMapper;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_CATEGORY_NOT_EMPTY;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_CATEGORY_NOT_EXISTS;

/**
 * 软件分类 Service 实现类（013）
 */
@Service
@Validated
public class SoftwareCategoryServiceImpl implements SoftwareCategoryService {

    @Resource
    private SoftwareCategoryMapper categoryMapper;
    @Resource
    private SoftwareMapper softwareMapper;

    @Override
    public Long createCategory(SoftwareCategoryDO category) {
        category.setId(null);
        if (category.getSort() == null) {
            category.setSort(0);
        }
        if (category.getStatus() == null) {
            category.setStatus(0);
        }
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public void updateCategory(SoftwareCategoryDO category) {
        validateCategoryExists(category.getId());
        categoryMapper.updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        validateCategoryExists(id);
        // 分类下仍有未删除的软件时拒绝删除（FR-330 / contracts/admin-api §1）
        if (softwareMapper.selectCountByCategoryId(id) > 0) {
            throw exception(SOFTWARE_CATEGORY_NOT_EMPTY);
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public SoftwareCategoryDO getCategory(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public PageResult<SoftwareCategoryDO> getCategoryPage(String name, Integer status, PageParam pageParam) {
        return categoryMapper.selectPage(name, status, pageParam);
    }

    @Override
    public List<SoftwareCategoryDO> getEnabledCategoryList() {
        return categoryMapper.selectListByStatus(0);
    }

    private void validateCategoryExists(Long id) {
        if (id == null || categoryMapper.selectById(id) == null) {
            throw exception(SOFTWARE_CATEGORY_NOT_EXISTS);
        }
    }

}
