package cn.iocoder.yudao.module.product.controller.admin.software;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.AdminSoftwareCategoryPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.AdminSoftwareCategoryRespVO;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.AdminSoftwareCategorySaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;
import cn.iocoder.yudao.module.product.service.software.SoftwareCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_CATEGORY_NOT_EMPTY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 管理端软件分类（013 / US50）
 */
@ExtendWith(MockitoExtension.class)
public class AdminSoftwareCategoryControllerTest {

    @Mock
    private SoftwareCategoryService categoryService;
    @InjectMocks
    private AdminSoftwareCategoryController controller;

    private SoftwareCategoryDO category(long id, String name, int sort, int status) {
        return SoftwareCategoryDO.builder().id(id).name(name).sort(sort).status(status).build();
    }

    @Test
    void pageFiltersByNameAndStatus() {
        when(categoryService.getCategoryPage(eq("驱动"), eq(0), any()))
                .thenReturn(new PageResult<>(List.of(category(1L, "打印驱动", 0, 0)), 1L));

        AdminSoftwareCategoryPageReqVO reqVO = new AdminSoftwareCategoryPageReqVO();
        reqVO.setName("驱动");
        reqVO.setStatus(0);

        PageResult<AdminSoftwareCategoryRespVO> result = controller.getCategoryPage(reqVO).getData();

        assertEquals(1, result.getList().size());
        assertEquals("打印驱动", result.getList().get(0).getName());
    }

    @Test
    void listReturnsEnabledCategories() {
        when(categoryService.getEnabledCategoryList())
                .thenReturn(List.of(category(1L, "打印驱动", 0, 0), category(2L, "标签软件", 1, 0)));

        assertEquals(2, controller.getCategoryList().getData().size());
    }

    @Test
    void createPassesPayload() {
        when(categoryService.createCategory(any())).thenReturn(1L);

        AdminSoftwareCategorySaveReqVO reqVO = new AdminSoftwareCategorySaveReqVO();
        reqVO.setName("打印驱动");
        reqVO.setSort(10);
        reqVO.setStatus(0);

        assertEquals(1L, controller.createCategory(reqVO).getData());

        ArgumentCaptor<SoftwareCategoryDO> captor = ArgumentCaptor.forClass(SoftwareCategoryDO.class);
        verify(categoryService).createCategory(captor.capture());
        assertEquals("打印驱动", captor.getValue().getName());
        assertEquals(10, captor.getValue().getSort());
    }

    @Test
    void updatePassesId() {
        AdminSoftwareCategorySaveReqVO reqVO = new AdminSoftwareCategorySaveReqVO();
        reqVO.setId(1L);
        reqVO.setName("改名");
        reqVO.setSort(5);

        assertTrue(controller.updateCategory(reqVO).getData());

        ArgumentCaptor<SoftwareCategoryDO> captor = ArgumentCaptor.forClass(SoftwareCategoryDO.class);
        verify(categoryService).updateCategory(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals("改名", captor.getValue().getName());
    }

    @Test
    void deleteDelegates() {
        assertTrue(controller.deleteCategory(1L).getData());
        verify(categoryService).deleteCategory(1L);
    }

    /** 分类下仍有软件时删除被拒（契约 §1） */
    @Test
    void deleteRejectedWhenCategoryNotEmpty() {
        doThrow(new ServiceException(SOFTWARE_CATEGORY_NOT_EMPTY)).when(categoryService).deleteCategory(1L);

        ServiceException e = assertThrows(ServiceException.class, () -> controller.deleteCategory(1L));
        assertEquals(SOFTWARE_CATEGORY_NOT_EMPTY.getCode(), e.getCode());
    }

}
