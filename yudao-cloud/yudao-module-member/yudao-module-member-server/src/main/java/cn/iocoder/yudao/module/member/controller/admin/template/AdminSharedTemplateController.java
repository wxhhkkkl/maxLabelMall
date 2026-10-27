package cn.iocoder.yudao.module.member.controller.admin.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminSharedTemplateAuditReqVO;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminSharedTemplateRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.template.MaxLabelTemplateSketch;
import cn.iocoder.yudao.module.member.service.template.SharedTemplateService;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MaxLabel 共享模板审核")
@RestController
@RequestMapping("/member/shared-template")
@Validated
public class AdminSharedTemplateController {

    @Resource
    private SharedTemplateService sharedTemplateService;
    @Resource
    private MemberUserService memberUserService;

    @GetMapping("/page")
    @Operation(summary = "获得共享模板审核分页")
    @PreAuthorize("@ss.hasPermission('member:shared-template:query')")
    public CommonResult<PageResult<AdminSharedTemplateRespVO>> getPage(@Valid AdminSharedTemplatePageReqVO reqVO) {
        PageResult<MaxLabelSharedTemplateDO> page = sharedTemplateService.getAdminPage(reqVO);
        List<AdminSharedTemplateRespVO> list = page.getList().stream().map(this::toRespVO).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得共享模板审核详情")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('member:shared-template:query')")
    public CommonResult<AdminSharedTemplateRespVO> get(@RequestParam("id") Long id) {
        return success(toRespVO(sharedTemplateService.getAdmin(id)));
    }

    @PutMapping("/approve")
    @Operation(summary = "审核通过")
    @PreAuthorize("@ss.hasPermission('member:shared-template:audit')")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id) {
        sharedTemplateService.approve(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "审核驳回")
    @PreAuthorize("@ss.hasPermission('member:shared-template:audit')")
    public CommonResult<Boolean> reject(@Valid @RequestBody AdminSharedTemplateAuditReqVO reqVO) {
        sharedTemplateService.reject(reqVO.getId(), SecurityFrameworkUtils.getLoginUserId(), reqVO.getReason());
        return success(true);
    }

    @PutMapping("/offline")
    @Operation(summary = "下架共享模板")
    @PreAuthorize("@ss.hasPermission('member:shared-template:offline')")
    public CommonResult<Boolean> offline(@RequestParam("id") Long id) {
        sharedTemplateService.offline(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    private AdminSharedTemplateRespVO toRespVO(MaxLabelSharedTemplateDO shared) {
        AdminSharedTemplateRespVO resp = BeanUtils.toBean(shared, AdminSharedTemplateRespVO.class);
        resp.setThumb(MaxLabelTemplateSketch.dataUrl(shared.getContent(), shared.getWidthMm(), shared.getHeightMm()));
        MemberUserDO user = memberUserService.getUser(shared.getPublisherMemberId());
        if (user != null) {
            resp.setPublisherNickname(user.getNickname());
            String mobile = user.getMobile();
            resp.setPublisherMobile(mobile == null || mobile.length() < 7 ? mobile
                    : mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4));
        }
        return resp;
    }

}
