package cn.iocoder.yudao.module.product.controller.admin.software;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.*;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import cn.iocoder.yudao.module.product.service.software.SoftwareService;
import cn.iocoder.yudao.module.product.service.software.SoftwareVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 管理端软件接口（013 / US47）
 */
@ExtendWith(MockitoExtension.class)
public class AdminSoftwareControllerTest {

    @Mock
    private SoftwareService softwareService;
    @Mock
    private SoftwareVersionService versionService;
    @InjectMocks
    private AdminSoftwareController controller;

    private SoftwareDO software() {
        return SoftwareDO.builder().id(10L).categoryId(1L).title("MaxLabel")
                .introduction("介绍").type(1).showInSupport(true).status(0).build();
    }

    @Test
    void pageMapsFilterAndResult() {
        when(softwareService.getSoftwarePage(eq(1L), eq(1), eq(0), eq("Max"), any()))
                .thenReturn(new PageResult<>(List.of(software()), 1L));

        AdminSoftwarePageReqVO reqVO = new AdminSoftwarePageReqVO();
        reqVO.setCategoryId(1L);
        reqVO.setType(1);
        reqVO.setStatus(0);
        reqVO.setTitle("Max");

        PageResult<AdminSoftwareRespVO> result = controller.getSoftwarePage(reqVO).getData();

        assertEquals(1, result.getList().size());
        assertEquals("MaxLabel", result.getList().get(0).getTitle());
        assertTrue(result.getList().get(0).getShowInSupport());
    }

    @Test
    void detailIncludesAllVersions() {
        when(softwareService.getSoftware(10L)).thenReturn(software());
        when(versionService.getVersionPage(eq(10L), any()))
                .thenReturn(new PageResult<>(List.of(
                        SoftwareVersionDO.builder().id(1L).softwareId(10L).version("1.0.2")
                                .platform("windows").arch("x64").currentFlag(1).status(0).build(),
                        SoftwareVersionDO.builder().id(2L).softwareId(10L).version("1.0.1")
                                .platform("windows").arch("x64").currentFlag(null).status(2).build()), 2L));

        AdminSoftwareDetailRespVO detail = controller.getSoftware(10L).getData();

        assertEquals("MaxLabel", detail.getTitle());
        assertEquals(2, detail.getVersions().size());
    }

    @Test
    void detailOfMissingSoftwareReturnsNull() {
        when(softwareService.getSoftware(999L)).thenReturn(null);

        assertNull(controller.getSoftware(999L).getData());
    }

    @Test
    void createPassesThroughPayload() {
        when(softwareService.createSoftware(any())).thenReturn(10L);

        AdminSoftwareSaveReqVO reqVO = new AdminSoftwareSaveReqVO();
        reqVO.setCategoryId(1L);
        reqVO.setTitle("MaxLabel");
        reqVO.setType(1);
        reqVO.setShowInSupport(true);

        assertEquals(10L, controller.createSoftware(reqVO).getData());

        ArgumentCaptor<SoftwareDO> captor = ArgumentCaptor.forClass(SoftwareDO.class);
        verify(softwareService).createSoftware(captor.capture());
        assertEquals(1L, captor.getValue().getCategoryId());
        assertEquals("MaxLabel", captor.getValue().getTitle());
        assertEquals(1, captor.getValue().getType());
    }

    @Test
    void updatePassesThroughId() {
        AdminSoftwareSaveReqVO reqVO = new AdminSoftwareSaveReqVO();
        reqVO.setId(10L);
        reqVO.setCategoryId(1L);
        reqVO.setTitle("改名");
        reqVO.setType(1);

        assertTrue(controller.updateSoftware(reqVO).getData());

        ArgumentCaptor<SoftwareDO> captor = ArgumentCaptor.forClass(SoftwareDO.class);
        verify(softwareService).updateSoftware(captor.capture());
        assertEquals(10L, captor.getValue().getId());
        assertEquals("改名", captor.getValue().getTitle());
    }

    @Test
    void deleteDelegates() {
        assertTrue(controller.deleteSoftware(10L).getData());
        verify(softwareService).deleteSoftware(10L);
    }

    @Test
    void pageUsesDefaultPagination() {
        when(softwareService.getSoftwarePage(any(), any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0L));

        controller.getSoftwarePage(new AdminSoftwarePageReqVO());

        ArgumentCaptor<PageParam> captor = ArgumentCaptor.forClass(PageParam.class);
        verify(softwareService).getSoftwarePage(any(), any(), any(), any(), captor.capture());
        assertEquals(1, captor.getValue().getPageNo());
    }

}
