package cn.iocoder.yudao.module.member.controller.admin.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminLabelTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import cn.iocoder.yudao.module.member.dal.mysql.template.MaxLabelSharedTemplateMapper;
import cn.iocoder.yudao.module.member.service.template.LabelTemplateService;
import cn.iocoder.yudao.module.member.service.template.MaxLabelTemplateSketch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MaxLabel 会员模板")
@RestController
@RequestMapping("/member/cloud-template")
@Validated
public class AdminLabelTemplateController {

    @Resource
    private LabelTemplateService templateService;
    @Resource
    private MaxLabelSharedTemplateMapper sharedTemplateMapper;

    @GetMapping("/page")
    @Operation(summary = "获得会员云端模板分页")
    @PreAuthorize("@ss.hasPermission('member:cloud-template:query')")
    public CommonResult<PageResult<AppLabelTemplateRespVO>> getPage(@Valid AdminLabelTemplatePageReqVO reqVO) {
        PageResult<MaxLabelLabelTemplateDO> page = templateService.getPage(reqVO.getUserId(), reqVO.getKeyword(), reqVO);
        Map<Long, MaxLabelSharedTemplateDO> shared = sharedMap(page.getList().stream()
                .map(MaxLabelLabelTemplateDO::getId).toList());
        List<AppLabelTemplateRespVO> list = page.getList().stream()
                .map(template -> toRespVO(template, shared.get(template.getId())))
                .toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得会员云端模板正文（后台预览/下载）")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('member:cloud-template:query')")
    public CommonResult<AppLabelTemplateContentRespVO> get(@RequestParam("id") Long id) {
        MaxLabelLabelTemplateDO template = templateService.getById(id);
        MaxLabelSharedTemplateDO shared = sharedTemplateMapper.selectBySourceTemplateId(id);
        AppLabelTemplateContentRespVO resp = BeanUtils.toBean(template, AppLabelTemplateContentRespVO.class);
        resp.setShareStatus(shared == null ? null : shared.getAuditStatus());
        resp.setThumb(MaxLabelTemplateSketch.dataUrl(template.getContent(), template.getWidthMm(), template.getHeightMm()));
        return success(resp);
    }

    private Map<Long, MaxLabelSharedTemplateDO> sharedMap(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return sharedTemplateMapper.selectListBySourceTemplateIds(ids).stream()
                .collect(Collectors.toMap(MaxLabelSharedTemplateDO::getSourceTemplateId, Function.identity()));
    }

    private AppLabelTemplateRespVO toRespVO(MaxLabelLabelTemplateDO template, MaxLabelSharedTemplateDO shared) {
        AppLabelTemplateRespVO resp = BeanUtils.toBean(template, AppLabelTemplateRespVO.class);
        resp.setShareStatus(shared == null ? null : shared.getAuditStatus());
        resp.setThumb(MaxLabelTemplateSketch.dataUrl(template.getContent(), template.getWidthMm(), template.getHeightMm()));
        return resp;
    }

}
