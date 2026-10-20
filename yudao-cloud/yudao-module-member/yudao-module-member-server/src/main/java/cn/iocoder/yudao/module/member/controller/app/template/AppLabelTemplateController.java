package cn.iocoder.yudao.module.member.controller.app.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.template.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import cn.iocoder.yudao.module.member.dal.mysql.template.MaxLabelSharedTemplateMapper;
import cn.iocoder.yudao.module.member.service.template.LabelTemplateService;
import cn.iocoder.yudao.module.member.service.template.SharedTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 云端模板")
@RestController
@RequestMapping("/member/cloud-template")
@Validated
public class AppLabelTemplateController {

    @Resource
    private LabelTemplateService templateService;
    @Resource
    private SharedTemplateService sharedTemplateService;
    @Resource
    private MaxLabelSharedTemplateMapper sharedTemplateMapper;

    @GetMapping("/page")
    @Operation(summary = "获得我的云端模板分页")
    public CommonResult<PageResult<AppLabelTemplateRespVO>> getPage(@Valid AppLabelTemplatePageReqVO reqVO) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        PageResult<MaxLabelLabelTemplateDO> page = templateService.getPage(memberId, reqVO.getKeyword(), reqVO);
        Map<Long, MaxLabelSharedTemplateDO> shared = sharedMap(page.getList().stream()
                .map(MaxLabelLabelTemplateDO::getId).toList());
        List<AppLabelTemplateRespVO> list = page.getList().stream()
                .map(template -> toRespVO(template, shared.get(template.getId())))
                .toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得我的云端模板正文")
    @Parameter(name = "id", required = true)
    public CommonResult<AppLabelTemplateContentRespVO> get(@RequestParam("id") Long id) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        MaxLabelLabelTemplateDO template = templateService.getContent(memberId, id);
        MaxLabelSharedTemplateDO shared = sharedTemplateMapper.selectBySourceTemplateId(id);
        AppLabelTemplateContentRespVO resp = BeanUtils.toBean(template, AppLabelTemplateContentRespVO.class);
        resp.setShareStatus(shared == null ? null : shared.getAuditStatus());
        return success(resp);
    }

    @PostMapping("/create")
    @Operation(summary = "创建我的云端模板")
    public CommonResult<Long> create(@Valid @RequestBody AppLabelTemplateSaveReqVO reqVO) {
        return success(templateService.create(SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新我的云端模板")
    public CommonResult<Boolean> update(@Valid @RequestBody AppLabelTemplateSaveReqVO reqVO) {
        templateService.update(SecurityFrameworkUtils.getLoginUserId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除我的云端模板")
    @Parameter(name = "id", required = true)
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        templateService.delete(SecurityFrameworkUtils.getLoginUserId(), id);
        return success(true);
    }

    @PostMapping("/submit-share")
    @Operation(summary = "提交共享审核")
    public CommonResult<Map<String, Object>> submitShare(@Valid @RequestBody AppLabelTemplateSubmitShareReqVO reqVO) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        MaxLabelLabelTemplateDO template = templateService.getOwned(memberId, reqVO.getId());
        MaxLabelSharedTemplateDO shared = sharedTemplateService.submit(
                memberId, template, reqVO.getCategory(), reqVO.getKeywords());
        return success(Map.of("sharedTemplateId", shared.getId(), "auditStatus", shared.getAuditStatus()));
    }

    @DeleteMapping("/cancel-share")
    @Operation(summary = "取消共享")
    @Parameter(name = "id", required = true)
    public CommonResult<Boolean> cancelShare(@RequestParam("id") Long id) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        templateService.getOwned(memberId, id);
        sharedTemplateService.cancel(memberId, id);
        return success(true);
    }

    private Map<Long, MaxLabelSharedTemplateDO> sharedMap(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return sharedTemplateMapper.selectListBySourceTemplateIds(ids).stream()
                .collect(Collectors.toMap(MaxLabelSharedTemplateDO::getSourceTemplateId, Function.identity()));
    }

    private AppLabelTemplateRespVO toRespVO(MaxLabelLabelTemplateDO template, MaxLabelSharedTemplateDO shared) {
        AppLabelTemplateRespVO resp = BeanUtils.toBean(template, AppLabelTemplateRespVO.class);
        resp.setShareStatus(shared == null ? null : shared.getAuditStatus());
        return resp;
    }

}
