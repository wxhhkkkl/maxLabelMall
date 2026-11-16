package cn.iocoder.yudao.module.member.controller.admin.labelshop;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.labelshop.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.service.labelshop.LabelshopImportService;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopPrecheck;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_LABELSHOP_SUBJECT_REQUIRED;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_LABELSHOP_TASK_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminLabelshopImportControllerTest {

    @Mock private LabelshopImportService importService;
    @Mock private LabelshopImportVoAssembler assembler;

    @InjectMocks private AdminLabelshopImportController controller;

    @Test
    void start_returnsTaskId() {
        when(importService.start(any(), any(), any(), any(), any(), any())).thenReturn(9L);

        CommonResult<AdminLabelshopImportStartRespVO> result = controller.start(startReq());

        assertEquals(9L, result.getData().getTaskId());
    }

    @Test
    void start_rejectsMemberSubjectOnOrgEndpoint() {
        AdminLabelshopImportStartReqVO req = startReq();
        req.setOrgId(null);
        req.setMemberId(1001L);

        assertError(MEMBER_LABELSHOP_SUBJECT_REQUIRED.getCode(), () -> controller.start(req));
        verify(importService, never()).startForMember(any(), any(), any(), any(), any(), any());
    }

    @Test
    void start_isProtectedByOrgPermission() throws Exception {
        Method method = AdminLabelshopImportController.class.getMethod("start",
                AdminLabelshopImportStartReqVO.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("@ss.hasPermission('member:org:labelshop-import')", preAuthorize.value());
    }

    @Test
    void start_disablesAccessLogRequestBodyAndNeverEchoesPassword() throws Exception {
        Method method = AdminLabelshopImportController.class.getMethod("start",
                AdminLabelshopImportStartReqVO.class);
        ApiAccessLog apiAccessLog = method.getAnnotation(ApiAccessLog.class);

        assertNotNull(apiAccessLog);
        assertFalse(apiAccessLog.requestEnable());
        assertEquals(List.of("orgId", "memberId", "account", "password", "authorized"),
                java.util.Arrays.stream(AdminLabelshopImportStartReqVO.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName).toList());
    }

    @Test
    void start_logsOrgSubject() throws Exception {
        Method method = AdminLabelshopImportController.class.getMethod("start",
                AdminLabelshopImportStartReqVO.class);
        LogRecord logRecord = method.getAnnotation(LogRecord.class);

        assertNotNull(logRecord);
        assertEquals("{{#reqVO.orgId}}", logRecord.bizNo());
        assertEquals("发起了历史模板导入（企业【{{#reqVO.orgId}}】）", logRecord.success());
    }

    @Test
    void precheck_delegatesToAssembler() {
        LabelshopPrecheck precheck = new LabelshopPrecheck();
        AdminLabelshopImportPrecheckRespVO vo = new AdminLabelshopImportPrecheckRespVO();
        vo.setOrgName("云马科技");
        when(importService.getPrecheck(88L)).thenReturn(precheck);
        when(assembler.toPrecheckVO(precheck)).thenReturn(vo);

        assertEquals("云马科技", controller.precheck(88L).getData().getOrgName());
    }

    @Test
    void getTask_rejectsMemberTask() {
        when(importService.getTask(7L)).thenReturn(LabelshopImportTaskDO.builder()
                .id(7L).subjectType(LabelshopImportTaskDO.SUBJECT_MEMBER).build());

        assertError(MEMBER_LABELSHOP_TASK_NOT_FOUND.getCode(), () -> controller.getTask(7L));
    }

    @Test
    void getLatest_delegatesForOrg() {
        LabelshopImportTaskDO task = LabelshopImportTaskDO.builder()
                .id(9L).subjectType(LabelshopImportTaskDO.SUBJECT_ORG).build();
        AdminLabelshopImportTaskRespVO vo = new AdminLabelshopImportTaskRespVO();
        vo.setId(9L);
        when(importService.getLatest(88L)).thenReturn(task);
        when(assembler.toTaskVO(task)).thenReturn(vo);

        assertEquals(9L, controller.getLatest(88L).getData().getId());
    }

    @Test
    void getLatest_returnsNullWhenNoTask() {
        when(importService.getLatest(88L)).thenReturn(null);

        assertNull(controller.getLatest(88L).getData());
    }

    @Test
    void getItems_rejectsMemberTask() {
        when(importService.getTask(9L)).thenReturn(LabelshopImportTaskDO.builder()
                .id(9L).subjectType(LabelshopImportTaskDO.SUBJECT_MEMBER).build());
        AdminLabelshopImportItemsPageReqVO req = itemsReq();

        assertError(MEMBER_LABELSHOP_TASK_NOT_FOUND.getCode(), () -> controller.getItems(req));
        verify(importService, never()).getItems(any(), any(), any(), any());
    }

    @Test
    void getItems_mapsPage() {
        LabelshopImportItemDO item = LabelshopImportItemDO.builder()
                .id(77L).sourceLibrary(12).sourceName("P1").status(3).errorMessage("x").build();
        AdminLabelshopImportItemRespVO itemVO = new AdminLabelshopImportItemRespVO();
        itemVO.setId(77L);
        itemVO.setSourceName("P1");
        LabelshopImportTaskDO task = LabelshopImportTaskDO.builder()
                .id(9L).subjectType(LabelshopImportTaskDO.SUBJECT_ORG).build();
        when(importService.getTask(9L)).thenReturn(task);
        when(importService.getItems(eq(9L), eq(3), eq(12), any()))
                .thenReturn(new PageResult<>(List.of(item), 1L));
        when(assembler.toItemVO(item)).thenReturn(itemVO);

        CommonResult<PageResult<AdminLabelshopImportItemRespVO>> result =
                controller.getItems(itemsReq());

        assertEquals(1L, result.getData().getTotal());
        assertEquals("P1", result.getData().getList().get(0).getSourceName());
    }

    private static AdminLabelshopImportItemsPageReqVO itemsReq() {
        AdminLabelshopImportItemsPageReqVO req = new AdminLabelshopImportItemsPageReqVO();
        req.setTaskId(9L);
        req.setStatus(3);
        req.setLibrary(12);
        req.setPageNo(1);
        req.setPageSize(10);
        return req;
    }

    private static AdminLabelshopImportStartReqVO startReq() {
        AdminLabelshopImportStartReqVO req = new AdminLabelshopImportStartReqVO();
        req.setOrgId(88L);
        req.setAccount("user@example.com");
        req.setPassword("pwd");
        req.setAuthorized(true);
        return req;
    }

    private static void assertError(int code, Runnable runnable) {
        ServiceException exception = assertThrows(ServiceException.class, runnable::run);
        assertEquals(code, exception.getCode());
    }

}
