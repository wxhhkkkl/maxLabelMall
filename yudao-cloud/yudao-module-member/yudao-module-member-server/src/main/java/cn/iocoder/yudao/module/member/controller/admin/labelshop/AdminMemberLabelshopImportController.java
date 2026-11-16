package cn.iocoder.yudao.module.member.controller.admin.labelshop;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.admin.labelshop.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportTaskDO;
import cn.iocoder.yudao.module.member.service.labelshop.LabelshopImportService;
import com.mzt.logapi.starter.annotation.LogRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_LABELSHOP_SUBJECT_REQUIRED;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_LABELSHOP_TASK_NOT_FOUND;
import static cn.iocoder.yudao.module.member.enums.LogRecordConstants.MEMBER_LABELSHOP_START_SUCCESS_MEMBER;
import static cn.iocoder.yudao.module.member.enums.LogRecordConstants.MEMBER_LABELSHOP_START_SUB_TYPE;
import static cn.iocoder.yudao.module.member.enums.LogRecordConstants.MEMBER_LABELSHOP_TYPE;

/**
 * 管理后台 - 个人会员代导历史模板。
 */
@Tag(name = "管理后台 - 导入历史模板（个人会员）")
@RestController
@RequestMapping("/member/user/labelshop-import")
@Validated
public class AdminMemberLabelshopImportController {

    @Resource
    private LabelshopImportService importService;
    @Resource
    private LabelshopImportVoAssembler assembler;

    @GetMapping("/precheck")
    @Operation(summary = "导入前置检查")
    @PreAuthorize("@ss.hasPermission('member:user:labelshop-import')")
    public CommonResult<AdminLabelshopImportPrecheckRespVO> precheck(
            @RequestParam("memberId") Long memberId) {
        return success(assembler.toPrecheckVO(importService.getMemberPrecheck(memberId)));
    }

    @PostMapping("/start")
    @Operation(summary = "发起历史模板导入")
    @PreAuthorize("@ss.hasPermission('member:user:labelshop-import')")
    @ApiAccessLog(requestEnable = false)
    @LogRecord(type = MEMBER_LABELSHOP_TYPE, subType = MEMBER_LABELSHOP_START_SUB_TYPE,
            bizNo = "{{#reqVO.memberId}}", success = MEMBER_LABELSHOP_START_SUCCESS_MEMBER)
    public CommonResult<AdminLabelshopImportStartRespVO> start(
            @Valid @RequestBody AdminLabelshopImportStartReqVO reqVO) {
        if (reqVO.getMemberId() == null || reqVO.getOrgId() != null) {
            throw exception(MEMBER_LABELSHOP_SUBJECT_REQUIRED);
        }
        Long taskId = importService.startForMember(reqVO.getMemberId(), reqVO.getAccount(),
                reqVO.getPassword(), reqVO.getAuthorized(),
                SecurityFrameworkUtils.getLoginUserId(), SecurityFrameworkUtils.getLoginUserNickname());
        return success(new AdminLabelshopImportStartRespVO(taskId));
    }

    @GetMapping("/task")
    @Operation(summary = "导入任务详情")
    @Parameter(name = "taskId", required = true)
    @PreAuthorize("@ss.hasPermission('member:user:labelshop-import')")
    public CommonResult<AdminLabelshopImportTaskRespVO> getTask(
            @RequestParam("taskId") Long taskId) {
        return success(assembler.toTaskVO(requireMemberTask(taskId)));
    }

    @GetMapping("/latest")
    @Operation(summary = "会员最近一次导入任务")
    @Parameter(name = "memberId", required = true)
    @PreAuthorize("@ss.hasPermission('member:user:labelshop-import')")
    public CommonResult<AdminLabelshopImportTaskRespVO> getLatest(
            @RequestParam("memberId") Long memberId) {
        LabelshopImportTaskDO task = importService.getLatestForMember(memberId);
        return success(task == null ? null : assembler.toTaskVO(task));
    }

    @GetMapping("/items")
    @Operation(summary = "导入明细分页")
    @PreAuthorize("@ss.hasPermission('member:user:labelshop-import')")
    public CommonResult<PageResult<AdminLabelshopImportItemRespVO>> getItems(
            @Valid AdminLabelshopImportItemsPageReqVO reqVO) {
        requireMemberTask(reqVO.getTaskId());
        PageResult<LabelshopImportItemDO> page = importService.getItems(
                reqVO.getTaskId(), reqVO.getStatus(), reqVO.getLibrary(), reqVO);
        List<AdminLabelshopImportItemRespVO> list = page.getList().stream()
                .map(assembler::toItemVO).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    /** 会员端点不允许读取企业导入任务，避免越权 */
    private LabelshopImportTaskDO requireMemberTask(Long taskId) {
        LabelshopImportTaskDO task = importService.getTask(taskId);
        if (!Objects.equals(task.getSubjectType(), LabelshopImportTaskDO.SUBJECT_MEMBER)) {
            throw exception(MEMBER_LABELSHOP_TASK_NOT_FOUND);
        }
        return task;
    }

}
