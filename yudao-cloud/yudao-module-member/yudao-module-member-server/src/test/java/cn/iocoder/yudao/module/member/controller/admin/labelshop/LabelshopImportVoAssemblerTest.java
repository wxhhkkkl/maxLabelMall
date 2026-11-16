package cn.iocoder.yudao.module.member.controller.admin.labelshop;

import cn.iocoder.yudao.module.member.controller.admin.labelshop.vo.AdminLabelshopImportItemRespVO;
import cn.iocoder.yudao.module.member.controller.admin.labelshop.vo.AdminLabelshopImportPrecheckRespVO;
import cn.iocoder.yudao.module.member.controller.admin.labelshop.vo.AdminLabelshopImportTaskRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportItemMapper;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopPrecheck;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import cn.iocoder.yudao.module.member.service.share.ShareSpaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LabelshopImportVoAssemblerTest {

    @Mock private MaxLabelOrgService orgService;
    @Mock private ShareSpaceService spaceService;
    @Mock private LabelshopImportItemMapper itemMapper;

    @InjectMocks private LabelshopImportVoAssembler assembler;

    @Test
    void toPrecheckVO_mapsAllFields() {
        LabelshopPrecheck precheck = new LabelshopPrecheck();
        precheck.setOrgId(88L);
        precheck.setOrgName("云马科技");
        precheck.setOwnerMemberId(1001L);
        precheck.setOwnerNickname("张三");
        precheck.setOwnerMobileMasked("138****8000");
        precheck.setOrgStatus(0);
        precheck.setLicenseActive(true);
        precheck.setLicenseEndDate(LocalDate.of(2027, 10, 26));
        precheck.setSeatCount(3);
        precheck.setFolderName("20261112173000导入");
        precheck.setPersonalEdition("professional");
        precheck.setPersonalUsedBytes(10L);
        precheck.setPersonalQuotaBytes(100L);
        precheck.setEnterpriseUsedBytes(20L);
        precheck.setEnterpriseQuotaBytes(300L);
        precheck.setRunningTaskId(7L);

        AdminLabelshopImportPrecheckRespVO vo = assembler.toPrecheckVO(precheck);

        assertEquals(88L, vo.getOrgId());
        assertEquals("云马科技", vo.getOrgName());
        assertEquals(1001L, vo.getOwnerMemberId());
        assertEquals("张三", vo.getOwnerNickname());
        assertEquals("138****8000", vo.getOwnerMobile());
        assertEquals(0, vo.getOrgStatus());
        assertTrue(vo.getLicenseActive());
        assertEquals(LocalDate.of(2027, 10, 26), vo.getLicenseEndDate());
        assertEquals(3, vo.getSeatCount());
        assertEquals("20261112173000导入", vo.getFolderName());
        assertEquals("professional", vo.getPersonalEdition());
        assertEquals(10L, vo.getPersonalUsedBytes());
        assertEquals(100L, vo.getPersonalQuotaBytes());
        assertEquals(20L, vo.getEnterpriseUsedBytes());
        assertEquals(300L, vo.getEnterpriseQuotaBytes());
        assertEquals(7L, vo.getRunningTaskId());
    }

    @Test
    void toTaskVO_mapsOrgCapacityLibrariesAndArchiveCount() {
        LabelshopImportTaskDO task = LabelshopImportTaskDO.builder()
                .id(9L).subjectType(LabelshopImportTaskDO.SUBJECT_ORG)
                .orgId(88L).ownerMemberId(1001L).folderId(500L).folderName("导入")
                .sourceAccountMasked("us***@example.com").status(2).phase("FINISH")
                .totalCount(4).processedCount(4).successCount(2).skipCount(1).failCount(1)
                .personalBytes(10L).enterpriseBytes(20L)
                .operatorId(1L).operatorName("运营").build();
        MaxLabelOrgDO org = new MaxLabelOrgDO();
        org.setId(88L);
        org.setName("云马科技");
        when(orgService.getOrg(88L)).thenReturn(org);
        when(spaceService.getPersonalUsedBytes(1001L)).thenReturn(120L);
        when(spaceService.getPersonalQuotaBytes(1001L)).thenReturn(100L);
        when(spaceService.getEnterpriseUsedBytes(88L)).thenReturn(20L);
        Map<String, Object> mine = row(12, 1, 2);
        Map<String, Object> group = row(13, 2, 1);
        Map<String, Object> company = row(1, 3, 1);
        when(itemMapper.selectLibraryStats(9L)).thenReturn(List.of(mine, group, company));
        when(itemMapper.countByTaskAndFormat(9L, "cloud-json")).thenReturn(1L);

        AdminLabelshopImportTaskRespVO vo = assembler.toTaskVO(task);

        assertEquals(1, vo.getSubjectType());
        assertEquals("云马科技", vo.getOrgName());
        assertEquals("导入", vo.getFolderName());
        assertEquals(20L, vo.getEnterpriseBytes());
        assertEquals(120L, vo.getPersonalUsedBytes());
        assertEquals(100L, vo.getPersonalQuotaBytes());
        assertTrue(vo.getPersonalExceeded());
        assertEquals(20L, vo.getEnterpriseUsedBytes());
        assertEquals(314572800L, vo.getEnterpriseQuotaBytes());
        assertFalse(vo.getEnterpriseExceeded());
        assertEquals(3, vo.getLibraries().size());
        assertEquals(12, vo.getLibraries().get(0).getLibrary());
        assertEquals(2, vo.getLibraries().get(0).getSuccess());
        assertEquals(13, vo.getLibraries().get(1).getLibrary());
        assertEquals(1, vo.getLibraries().get(1).getSkip());
        assertEquals(1, vo.getLibraries().get(2).getLibrary());
        assertEquals(1, vo.getLibraries().get(2).getFail());
        assertEquals(1L, vo.getArchiveJsonCount());
        assertTrue(vo.getUncleaned());
        assertEquals("运营", vo.getOperatorName());
    }

    @Test
    void toTaskVO_mapsMemberTaskWithoutEnterprise() {
        LabelshopImportTaskDO task = LabelshopImportTaskDO.builder()
                .id(11L).subjectType(LabelshopImportTaskDO.SUBJECT_MEMBER)
                .ownerMemberId(1001L).status(2)
                .totalCount(1).processedCount(1).successCount(1).skipCount(0).failCount(0)
                .personalBytes(10L).enterpriseBytes(0L).build();
        when(spaceService.getPersonalUsedBytes(1001L)).thenReturn(10L);
        when(spaceService.getPersonalQuotaBytes(1001L)).thenReturn(100L);
        when(itemMapper.selectLibraryStats(11L)).thenReturn(List.of());
        when(itemMapper.countByTaskAndFormat(11L, "cloud-json")).thenReturn(0L);

        AdminLabelshopImportTaskRespVO vo = assembler.toTaskVO(task);

        assertNull(vo.getOrgId());
        assertNull(vo.getOrgName());
        assertNull(vo.getFolderName());
        assertEquals(0L, vo.getEnterpriseUsedBytes());
        assertEquals(0L, vo.getEnterpriseQuotaBytes());
        assertFalse(vo.getEnterpriseExceeded());
        verify(orgService, never()).getOrg(any());
        verify(spaceService, never()).getEnterpriseUsedBytes(any());
    }

    @Test
    void toItemVO_mapsFields() {
        LabelshopImportItemDO item = LabelshopImportItemDO.builder()
                .id(5L).sourceLibrary(13).sourceCategory("组模板库/上衣")
                .sourceName("坏模板").targetScope(2).format("lsdx").editable(true)
                .sizeBytes(0L).status(3).errorMessage("正文为空").build();

        AdminLabelshopImportItemRespVO vo = assembler.toItemVO(item);

        assertEquals(5L, vo.getId());
        assertEquals(13, vo.getSourceLibrary());
        assertEquals("组模板库/上衣", vo.getSourceCategory());
        assertEquals("坏模板", vo.getSourceName());
        assertEquals(2, vo.getTargetScope());
        assertEquals("lsdx", vo.getFormat());
        assertTrue(vo.getEditable());
        assertEquals(0L, vo.getSizeBytes());
        assertEquals(3, vo.getStatus());
        assertEquals("正文为空", vo.getErrorMessage());
    }

    private static Map<String, Object> row(int library, int status, int count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source_library", library);
        row.put("status", status);
        row.put("cnt", count);
        return row;
    }

}
