package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;

import java.util.List;

/**
 * 软件分类 Service（013）
 */
public interface SoftwareCategoryService {

    Long createCategory(SoftwareCategoryDO category);

    void updateCategory(SoftwareCategoryDO category);

    /** 分类下仍有未删除软件时抛 SOFTWARE_CATEGORY_NOT_EMPTY */
    void deleteCategory(Long id);

    SoftwareCategoryDO getCategory(Long id);

    PageResult<SoftwareCategoryDO> getCategoryPage(String name, Integer status, PageParam pageParam);

    /** 启用中的分类，按排序升序 */
    List<SoftwareCategoryDO> getEnabledCategoryList();

}
