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
public class AdminMemberLabelshopImportControllerTest {

    @Mock private LabelshopImportService importService;
    @Mock private LabelshopImportVoAssembler assembler;

    @InjectMocks private AdminMemberLabelshopImportController controller;

    @Test
    void start_returnsTaskId() {
        when(importService.startForMember(any(), any(), any(), any(), any(), any())).thenReturn(7L);

        CommonResult<AdminLabelshopImportStartRespVO> result = controller.start(memberReq());

        assertEquals(7L, result.getData().getTaskId());
    }

    @Test
    void start_rejectsOrgSubjectOnMemberEndpoint() {
        AdminLabelshopImportStartReqVO req = memberReq();
        req.setMemberId(null);
        req.setOrgId(88L);

        assertError(MEMBER_LABELSHOP_SUBJECT_REQUIRED.getCode(), () -> controller.start(req));
        verify(importService, never()).start(any(), any(), any(), any(), any(), any());
    }

    @Test
    void start_isProtectedByMemberPermission() throws Exception {
        Method method = AdminMemberLabelshopImportController.class.getMethod("start",
                AdminLabelshopImportStartReqVO.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("@ss.hasPermission('member:user:labelshop-import')", preAuthorize.value());
    }

    @Test
    void start_disablesAccessLogRequestBody() throws Exception {
        Method method = AdminMemberLabelshopImportController.class.getMethod("start",
                AdminLabelshopImportStartReqVO.class);
        ApiAccessLog apiAccessLog = method.getAnnotation(ApiAccessLog.class);

        assertNotNull(apiAccessLog);
        assertFalse(apiAccessLog.requestEnable());
    }

    @Test
    void start_logsMemberSubject() throws Exception {
        Method method = AdminMemberLabelshopImportController.class.getMethod("start",
                AdminLabelshopImportStartReqVO.class);
        LogRecord logRecord = method.getAnnotation(LogRecord.class);

        assertNotNull(logRecord);
        assertEquals("{{#reqVO.memberId}}", logRecord.bizNo());
        assertEquals("发起了历史模板导入（会员【{{#reqVO.memberId}}】）", logRecord.success());
    }

    @Test
    void precheck_delegatesToMemberPrecheck() {
        LabelshopPrecheck precheck = new LabelshopPrecheck();
        AdminLabelshopImportPrecheckRespVO vo = new AdminLabelshopImportPrecheckRespVO();
        vo.setOwnerMemberId(1001L);
        when(importService.getMemberPrecheck(1001L)).thenReturn(precheck);
        when(assembler.toPrecheckVO(precheck)).thenReturn(vo);

        assertEquals(1001L, controller.precheck(1001L).getData().getOwnerMemberId());
    }

    @Test
    void getTask_rejectsOrgTask() {
        when(importService.getTask(9L)).thenReturn(LabelshopImportTaskDO.builder()
                .id(9L).subjectType(LabelshopImportTaskDO.SUBJECT_ORG).build());

        assertError(MEMBER_LABELSHOP_TASK_NOT_FOUND.getCode(), () -> controller.getTask(9L));
    }

    @Test
    void getLatest_delegatesForMember() {
        LabelshopImportTaskDO task = LabelshopImportTaskDO.builder()
                .id(7L).subjectType(LabelshopImportTaskDO.SUBJECT_MEMBER).build();
        AdminLabelshopImportTaskRespVO vo = new AdminLabelshopImportTaskRespVO();
        vo.setId(7L);
        when(importService.getLatestForMember(1001L)).thenReturn(task);
        when(assembler.toTaskVO(task)).thenReturn(vo);

        assertEquals(7L, controller.getLatest(1001L).getData().getId());
    }

    @Test
    void getItems_rejectsOrgTask() {
        when(importService.getTask(9L)).thenReturn(LabelshopImportTaskDO.builder()
                .id(9L).subjectType(LabelshopImportTaskDO.SUBJECT_ORG).build());
        AdminLabelshopImportItemsPageReqVO req = itemsReq();

        assertError(MEMBER_LABELSHOP_TASK_NOT_FOUND.getCode(), () -> controller.getItems(req));
        verify(importService, never()).getItems(any(), any(), any(), any());
    }

    @Test
    void getItems_mapsPage() {
        LabelshopImportItemDO item = LabelshopImportItemDO.builder()
                .id(77L).sourceLibrary(12).sourceName("P1").status(1).build();
        AdminLabelshopImportItemRespVO itemVO = new AdminLabelshopImportItemRespVO();
        itemVO.setId(77L);
        itemVO.setSourceName("P1");
        when(importService.getTask(7L)).thenReturn(LabelshopImportTaskDO.builder()
                .id(7L).subjectType(LabelshopImportTaskDO.SUBJECT_MEMBER).build());
        when(importService.getItems(eq(7L), eq(1), eq(12), any()))
                .thenReturn(new PageResult<>(List.of(item), 1L));
        when(assembler.toItemVO(item)).thenReturn(itemVO);

        AdminLabelshopImportItemsPageReqVO req = itemsReq();
        req.setTaskId(7L);
        req.setStatus(1);
        CommonResult<PageResult<AdminLabelshopImportItemRespVO>> result = controller.getItems(req);

        assertEquals(1L, result.getData().getTotal());
        assertEquals("P1", result.getData().getList().get(0).getSourceName());
    }

    private static AdminLabelshopImportItemsPageReqVO itemsReq() {
        AdminLabelshopImportItemsPageReqVO req = new AdminLabelshopImportItemsPageReqVO();
        req.setTaskId(9L);
        req.setStatus(1);
        req.setLibrary(12);
        req.setPageNo(1);
        req.setPageSize(10);
        return req;
    }

    private static AdminLabelshopImportStartReqVO memberReq() {
        AdminLabelshopImportStartReqVO req = new AdminLabelshopImportStartReqVO();
        req.setMemberId(1001L);
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
