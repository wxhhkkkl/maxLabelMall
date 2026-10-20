package cn.iocoder.yudao.module.member.controller.app.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplateContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplateRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.template.SharedTemplateService;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 共享模板")
@RestController
@RequestMapping("/member/shared-template")
@Validated
public class AppSharedTemplateController {

    @Resource
    private SharedTemplateService sharedTemplateService;
    @Resource
    private MemberUserService memberUserService;

    @GetMapping("/page")
    @Operation(summary = "获得平台共享模板分页")
    public CommonResult<PageResult<AppSharedTemplateRespVO>> getPage(@Valid AppSharedTemplatePageReqVO reqVO) {
        PageResult<MaxLabelSharedTemplateDO> page = sharedTemplateService.getPublicPage(reqVO);
        List<AppSharedTemplateRespVO> list = page.getList().stream().map(this::toRespVO).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得平台共享模板正文")
    @Parameter(name = "id", required = true)
    public CommonResult<AppSharedTemplateContentRespVO> get(@RequestParam("id") Long id) {
        MaxLabelSharedTemplateDO shared = sharedTemplateService.getPublicContent(id);
        AppSharedTemplateContentRespVO resp = BeanUtils.toBean(shared, AppSharedTemplateContentRespVO.class);
        resp.setPublisherNickname(publisherName(shared.getPublisherMemberId()));
        return success(resp);
    }

    private AppSharedTemplateRespVO toRespVO(MaxLabelSharedTemplateDO shared) {
        AppSharedTemplateRespVO resp = BeanUtils.toBean(shared, AppSharedTemplateRespVO.class);
        resp.setPublisherNickname(publisherName(shared.getPublisherMemberId()));
        return resp;
    }

    private String publisherName(Long memberId) {
        MemberUserDO user = memberUserService.getUser(memberId);
        if (user == null) return "匿名会员";
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname();
        String mobile = user.getMobile();
        if (mobile == null || mobile.length() < 7) return "匿名会员";
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

}
