package cn.iocoder.yudao.module.product.controller.app.software;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.product.controller.app.software.vo.AppSoftwareCategoryRespVO;
import cn.iocoder.yudao.module.product.controller.app.software.vo.AppSoftwareDetailRespVO;
import cn.iocoder.yudao.module.product.controller.app.software.vo.AppSoftwareRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import cn.iocoder.yudao.module.product.service.software.SoftwareCategoryService;
import cn.iocoder.yudao.module.product.service.software.SoftwareService;
import cn.iocoder.yudao.module.product.service.software.SoftwareVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 服务支持页 C 端接口（013 / US49 / FR-340~343）
 */
@ExtendWith(MockitoExtension.class)
public class AppSoftwareControllerTest {

    @Mock
    private SoftwareCategoryService categoryService;
    @Mock
    private SoftwareService softwareService;
    @Mock
    private SoftwareVersionService versionService;
    @InjectMocks
    private AppSoftwareController controller;

    private SoftwareCategoryDO category(long id, String name) {
        return SoftwareCategoryDO.builder().id(id).name(name).sort(0).status(0).build();
    }

    private SoftwareDO software(long id, long categoryId, boolean showInSupport) {
        return SoftwareDO.builder().id(id).categoryId(categoryId).title("软件" + id)
                .introduction("介绍").type(2).showInSupport(showInSupport).status(0).build();
    }

    private SoftwareVersionDO version(long softwareId, String ver, String platform) {
        return SoftwareVersionDO.builder().id(1L).softwareId(softwareId).version(ver)
                .platform(platform).arch("x64").fileName("a.exe").fileSize(100L)
                .fileUrl("https://img.yuwangchenfa.com/software/" + platform + "-x64/" + ver + "/a.exe")
                .sha512("S").currentFlag(1).status(0).build();
    }

    /** 只返回"下挂可展示软件"的分类，空分类不出现 */
    @Test
    void categoryListOnlyContainsCategoriesWithVisibleSoftware() {
        when(categoryService.getEnabledCategoryList())
                .thenReturn(List.of(category(1L, "打印驱动"), category(2L, "空分类")));
        when(softwareService.getSupportSoftwareList(any()))
                .thenReturn(List.of(software(10L, 1L, true)));

        List<AppSoftwareCategoryRespVO> list = controller.getCategoryList().getData();

        assertEquals(1, list.size());
        assertEquals("打印驱动", list.get(0).getName());
    }

    @Test
    void softwareListExposesLatestVersionAndPlatforms() {
        when(softwareService.getSupportSoftwareList(List.of(1L)))
                .thenReturn(List.of(software(10L, 1L, true)));
        when(versionService.getCurrentVersionList(List.of(10L)))
                .thenReturn(List.of(version(10L, "1.0.23", "windows"), version(10L, "1.0.23", "macos")));

        List<AppSoftwareRespVO> list = controller.getSoftwareList(1L).getData();

        assertEquals(1, list.size());
        assertEquals("1.0.23", list.get(0).getLatestVersion());
        assertEquals(List.of("windows", "macos"), list.get(0).getPlatforms());
    }

    /** 详情只含当前发布，且带 OSS 直链 */
    @Test
    void detailOnlyContainsCurrentVersionsWithDirectUrl() {
        when(softwareService.getSoftware(10L)).thenReturn(software(10L, 1L, true));
        when(versionService.getCurrentVersionList(List.of(10L)))
                .thenReturn(List.of(version(10L, "1.0.23", "windows")));

        AppSoftwareDetailRespVO detail = controller.getSoftware(10L).getData();

        assertEquals(1, detail.getVersions().size());
        assertEquals("https://img.yuwangchenfa.com/software/windows-x64/1.0.23/a.exe",
                detail.getVersions().get(0).getDownloadUrl());
    }

    /** 不在服务支持页展示的软件，直接访问详情按"不存在"处理 */
    /** 一屏平铺：一次拿到所有软件与各自的当前发布 */
    @Test
    void allReturnsEveryVisibleSoftwareWithVersions() {
        when(categoryService.getEnabledCategoryList())
                .thenReturn(List.of(category(1L, "打印驱动"), category(2L, "标签软件")));
        when(softwareService.getSupportSoftwareList(any()))
                .thenReturn(List.of(software(10L, 1L, true), software(11L, 2L, true)));
        when(versionService.getCurrentVersionList(any()))
                .thenReturn(List.of(version(10L, "1.0.1", "windows"), version(11L, "7.4.2", "windows")));

        List<AppSoftwareDetailRespVO> all = controller.getAllSoftware().getData();

        assertEquals(2, all.size());
        assertEquals("1.0.1", all.get(0).getVersions().get(0).getVersion());
        assertTrue(all.get(0).getVersions().get(0).getDownloadUrl().startsWith("https://img.yuwangchenfa.com/"));
    }

    @Test
    void allReturnsEmptyWhenNoCategory() {
        when(categoryService.getEnabledCategoryList()).thenReturn(List.of());
        assertTrue(controller.getAllSoftware().getData().isEmpty());
    }

    @Test
    void detailRejectedWhenNotShownInSupport() {
        when(softwareService.getSoftware(10L)).thenReturn(software(10L, 1L, false));

        ServiceException e = assertThrows(ServiceException.class, () -> controller.getSoftware(10L));
        assertEquals(SOFTWARE_NOT_EXISTS.getCode(), e.getCode());
    }

    @Test
    void detailRejectedWhenDisabled() {
        SoftwareDO disabled = software(10L, 1L, true);
        disabled.setStatus(1);
        when(softwareService.getSoftware(10L)).thenReturn(disabled);

        ServiceException e = assertThrows(ServiceException.class, () -> controller.getSoftware(10L));
        assertEquals(SOFTWARE_NOT_EXISTS.getCode(), e.getCode());
    }

    @Test
    void detailRejectedWhenMissing() {
        when(softwareService.getSoftware(999L)).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class, () -> controller.getSoftware(999L));
        assertEquals(SOFTWARE_NOT_EXISTS.getCode(), e.getCode());
    }

}
