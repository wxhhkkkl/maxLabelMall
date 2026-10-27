package cn.iocoder.yudao.module.product.service.software;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareCategoryMapper;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_CATEGORY_NOT_EMPTY;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_CATEGORY_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 软件分类（013 / FR-330）
 */
@Import({SoftwareCategoryServiceImpl.class})
public class SoftwareCategoryServiceImplTest extends BaseDbUnitTest {

    @Resource
    private SoftwareCategoryService categoryService;
    @Resource
    private SoftwareCategoryMapper categoryMapper;
    @Resource
    private SoftwareMapper softwareMapper;

    private Long create(String name, int sort, int status) {
        return categoryService.createCategory(SoftwareCategoryDO.builder()
                .name(name).sort(sort).status(status).build());
    }

    @Test
    void createAndQueryOrderedBySort() {
        create("打印驱动", 10, 0);
        create("标签打印软件", 0, 0);

        List<SoftwareCategoryDO> enabled = categoryService.getEnabledCategoryList();
        assertEquals(2, enabled.size());
        assertEquals("标签打印软件", enabled.get(0).getName());
        assertEquals("打印驱动", enabled.get(1).getName());
    }

    @Test
    void updateCategory() {
        Long id = create("旧名", 0, 0);
        categoryService.updateCategory(SoftwareCategoryDO.builder()
                .id(id).name("新名").sort(5).status(1).build());

        SoftwareCategoryDO updated = categoryService.getCategory(id);
        assertEquals("新名", updated.getName());
        assertEquals(5, updated.getSort());
        assertEquals(1, updated.getStatus());
    }

    @Test
    void updateUnknownCategoryRejected() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> categoryService.updateCategory(SoftwareCategoryDO.builder().id(9999L).name("x").build()));
        assertEquals(SOFTWARE_CATEGORY_NOT_EXISTS.getCode(), exception.getCode());
    }

    /** 停用的分类不进入启用列表（服务支持页据此隐藏） */
    @Test
    void disabledCategoryNotInEnabledList() {
        create("启用分类", 0, 0);
        create("停用分类", 1, 1);

        List<SoftwareCategoryDO> enabled = categoryService.getEnabledCategoryList();
        assertEquals(1, enabled.size());
        assertEquals("启用分类", enabled.get(0).getName());
    }

    /** 分类下仍有软件时拒绝删除（contracts/admin-api §1） */
    @Test
    void deleteRejectedWhenSoftwareExists() {
        Long categoryId = create("有软件的分类", 0, 0);
        softwareMapper.insert(SoftwareDO.builder()
                .categoryId(categoryId).title("某驱动").introduction("")
                .type(2).showInSupport(true).status(0).build());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> categoryService.deleteCategory(categoryId));
        assertEquals(SOFTWARE_CATEGORY_NOT_EMPTY.getCode(), exception.getCode());
        assertNotNull(categoryMapper.selectById(categoryId));
    }

    @Test
    void deleteEmptyCategory() {
        Long categoryId = create("空分类", 0, 0);

        categoryService.deleteCategory(categoryId);

        assertNull(categoryMapper.selectById(categoryId));
    }

    @Test
    void pageFiltersByNameAndStatus() {
        create("驱动 A", 0, 0);
        create("驱动 B", 1, 0);
        create("软件 C", 2, 1);

        assertEquals(2, categoryService.getCategoryPage("驱动", null, new PageParam()).getTotal());
        assertEquals(1, categoryService.getCategoryPage(null, 1, new PageParam()).getTotal());
    }

}
