package cn.iocoder.yudao.module.member.controller.app.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppPlatformContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplateContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplateRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentDO;
import cn.iocoder.yudao.module.member.service.share.PlatformContentService;
import cn.iocoder.yudao.module.member.service.template.MaxLabelTemplateSketch;
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
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 旧版平台共享模板库端点：保留路径与字段，内部代理到平台共享内容。
 */
@Tag(name = "用户 App - MaxLabel 共享模板（兼容）")
@RestController
@RequestMapping("/member/shared-template")
@Validated
public class AppSharedTemplateController {

    private static final String PUBLISHER = "平台运营";

    @Resource
    private PlatformContentService platformContentService;

    @GetMapping("/page")
    @Operation(summary = "获得平台共享内容分页")
    public CommonResult<PageResult<AppSharedTemplateRespVO>> getPage(@Valid AppSharedTemplatePageReqVO reqVO) {
        AppPlatformContentPageReqVO pageReq = new AppPlatformContentPageReqVO();
        pageReq.setKeyword(reqVO.getKeyword());
        pageReq.setCategory(reqVO.getCategory());
        pageReq.setWidthMin(reqVO.getWidthMin());
        pageReq.setWidthMax(reqVO.getWidthMax());
        pageReq.setHeightMin(reqVO.getHeightMin());
        pageReq.setHeightMax(reqVO.getHeightMax());
        pageReq.setPageNo(reqVO.getPageNo());
        pageReq.setPageSize(reqVO.getPageSize());
        PageResult<MaxLabelPlatformContentDO> page = platformContentService.getOnlinePage(pageReq);
        List<AppSharedTemplateRespVO> list = page.getList().stream().map(this::toRespVO).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得平台共享内容正文")
    @Parameter(name = "id", required = true)
    public CommonResult<AppSharedTemplateContentRespVO> get(@RequestParam("id") Long id) {
        MaxLabelPlatformContentDO content = platformContentService.getOnline(id);
        AppSharedTemplateContentRespVO resp = new AppSharedTemplateContentRespVO();
        copy(resp, content);
        resp.setContent(content.getContent());
        return success(resp);
    }

    private AppSharedTemplateRespVO toRespVO(MaxLabelPlatformContentDO content) {
        AppSharedTemplateRespVO resp = new AppSharedTemplateRespVO();
        copy(resp, content);
        return resp;
    }

    private void copy(AppSharedTemplateRespVO resp, MaxLabelPlatformContentDO content) {
        resp.setId(content.getId());
        resp.setName(content.getName());
        resp.setContentType(content.getContentType());
        resp.setWidthMm(content.getWidthMm());
        resp.setHeightMm(content.getHeightMm());
        resp.setCategory(content.getCategory());
        resp.setKeywords(content.getKeywords());
        resp.setPublisherNickname(PUBLISHER);
        resp.setPublishedTime(content.getPublishedTime());
        resp.setThumb(Objects.equals(content.getContentType(), 1) && content.getContent() != null
                ? MaxLabelTemplateSketch.dataUrl(content.getContent(), content.getWidthMm(), content.getHeightMm())
                : null);
    }

}
