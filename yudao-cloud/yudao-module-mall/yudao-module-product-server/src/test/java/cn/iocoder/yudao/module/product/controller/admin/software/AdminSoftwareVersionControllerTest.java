package cn.iocoder.yudao.module.product.controller.admin.software;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.AdminSoftwareVersionPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.AdminSoftwareVersionRegisterReqVO;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.AdminSoftwareVersionRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import cn.iocoder.yudao.module.product.service.software.SoftwareVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 管理端版本接口（013 / US47）
 */
@ExtendWith(MockitoExtension.class)
public class AdminSoftwareVersionControllerTest {

    @Mock
    private SoftwareVersionService versionService;
    @InjectMocks
    private AdminSoftwareVersionController controller;

    private SoftwareVersionDO version(Integer currentFlag, Integer status) {
        return SoftwareVersionDO.builder()
                .id(1L).softwareId(10L).version("1.0.2").platform("windows").arch("x64")
                .fileName("a.exe").fileSize(100L).sha512("x".repeat(88))
                .fileUrl("https://img.yuwangchenfa.com/a.exe").objectKey("software/windows-x64/1.0.2/a.exe")
                .currentFlag(currentFlag).status(status).build();
    }

    @Test
    void registerDelegatesAndReturnsId() {
        when(versionService.registerVersion(any())).thenReturn(1L);

        AdminSoftwareVersionRegisterReqVO reqVO = new AdminSoftwareVersionRegisterReqVO();
        reqVO.setSoftwareId(10L);
        reqVO.setVersion("1.0.2");
        reqVO.setPlatform("windows");
        reqVO.setArch("x64");
        reqVO.setFileName("a.exe");
        reqVO.setFileSize(100L);
        reqVO.setSha512("x".repeat(88));
        reqVO.setObjectKey("software/windows-x64/1.0.2/a.exe");
        reqVO.setFileUrl("https://img.yuwangchenfa.com/a.exe");

        assertEquals(1L, controller.registerVersion(reqVO).getData());

        ArgumentCaptor<SoftwareVersionDO> captor = ArgumentCaptor.forClass(SoftwareVersionDO.class);
        verify(versionService).registerVersion(captor.capture());
        assertEquals(10L, captor.getValue().getSoftwareId());
        assertEquals("1.0.2", captor.getValue().getVersion());
        assertEquals("windows", captor.getValue().getPlatform());
        assertEquals(100L, captor.getValue().getFileSize());
    }

    @Test
    void publishPassesCurrentFlag() {
        assertTrue(controller.publishVersion(1L, true).getData());
        verify(versionService).publishVersion(1L, true);
    }

    @Test
    void publishDefaultsToNotCurrent() {
        assertTrue(controller.publishVersion(1L, false).getData());
        verify(versionService).publishVersion(1L, false);
    }

    @Test
    void publishConflictPropagates() {
        doThrow(new ServiceException(SOFTWARE_VERSION_CONFLICT)).when(versionService).publishVersion(1L, true);

        ServiceException e = assertThrows(ServiceException.class, () -> controller.publishVersion(1L, true));
        assertEquals(SOFTWARE_VERSION_CONFLICT.getCode(), e.getCode());
    }

    @Test
    void unpublishDelegates() {
        assertTrue(controller.unpublishVersion(1L).getData());
        verify(versionService).unpublishVersion(1L);
    }

    @Test
    void deleteDelegates() {
        assertTrue(controller.deleteVersion(1L).getData());
        verify(versionService).deleteVersion(1L);
    }

    /** 列表要能区分「当前发布」，前端据此显示标记 */
    @Test
    void pageExposesCurrentFlag() {
        when(versionService.getVersionPage(eq(10L), any()))
                .thenReturn(new PageResult<>(List.of(version(1, 0), version(null, 2)), 2L));

        AdminSoftwareVersionPageReqVO reqVO = new AdminSoftwareVersionPageReqVO();
        reqVO.setSoftwareId(10L);

        PageResult<AdminSoftwareVersionRespVO> result = controller.getVersionPage(reqVO).getData();

        assertTrue(result.getList().get(0).getCurrent());
        assertFalse(result.getList().get(1).getCurrent());
    }

}
