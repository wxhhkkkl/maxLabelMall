package cn.iocoder.yudao.module.member.controller.app.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentSaveReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.service.share.ShareContentService;
import cn.iocoder.yudao.module.member.service.template.MaxLabelTemplateSketch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED;

/**
 * 旧版个人云端模板端点：保留路径与返回结构，内部代理到共享内容空间（个人 + 模板）。
 */
@Tag(name = "用户 App - MaxLabel 云端模板（兼容）")
@RestController
@RequestMapping("/member/cloud-template")
@Validated
public class AppLabelTemplateController {

    @Resource
    private ShareContentService contentService;

    @GetMapping("/page")
    @Operation(summary = "获得我的云端模板分页")
    public CommonResult<PageResult<AppLabelTemplateRespVO>> getPage(@Valid AppLabelTemplatePageReqVO reqVO) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        AppShareContentPageReqVO pageReq = new AppShareContentPageReqVO();
        pageReq.setScope(1);
        pageReq.setContentType(1);
        pageReq.setKeyword(reqVO.getKeyword());
        pageReq.setPageNo(reqVO.getPageNo());
        pageReq.setPageSize(reqVO.getPageSize());
        PageResult<MaxLabelShareContentDO> page = contentService.getPage(memberId, pageReq);
        List<AppLabelTemplateRespVO> list = page.getList().stream().map(this::toRespVO).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得我的云端模板正文")
    @Parameter(name = "id", required = true)
    public CommonResult<AppLabelTemplateContentRespVO> get(@RequestParam("id") Long id) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        MaxLabelShareContentDO content = contentService.getContent(memberId, id);
        AppLabelTemplateContentRespVO resp = new AppLabelTemplateContentRespVO();
        copy(resp, content);
        resp.setContent(content.getContent());
        return success(resp);
    }

    @PostMapping("/create")
    @Operation(summary = "创建我的云端模板")
    public CommonResult<Long> create(@Valid @RequestBody AppLabelTemplateSaveReqVO reqVO) {
        return success(contentService.create(SecurityFrameworkUtils.getLoginUserId(), toSaveReq(reqVO)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新我的云端模板")
    public CommonResult<Boolean> update(@Valid @RequestBody AppLabelTemplateSaveReqVO reqVO) {
        contentService.update(SecurityFrameworkUtils.getLoginUserId(), toSaveReq(reqVO));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除我的云端模板")
    @Parameter(name = "id", required = true)
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        contentService.delete(SecurityFrameworkUtils.getLoginUserId(), id);
        return success(true);
    }

    @PostMapping("/submit-share")
    @Operation(summary = "提交共享审核（已关闭）")
    public CommonResult<Object> submitShare(@Valid @RequestBody AppLabelTemplateSubmitShareReqVO reqVO) {
        throw exception(MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED);
    }

    @DeleteMapping("/cancel-share")
    @Operation(summary = "取消共享（已关闭）")
    @Parameter(name = "id", required = true)
    public CommonResult<Boolean> cancelShare(@RequestParam("id") Long id) {
        throw exception(MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED);
    }

    private AppShareContentSaveReqVO toSaveReq(AppLabelTemplateSaveReqVO reqVO) {
        AppShareContentSaveReqVO saveReq = new AppShareContentSaveReqVO();
        saveReq.setId(reqVO.getId());
        saveReq.setScope(1);
        saveReq.setContentType(1);
        saveReq.setName(withTemplateExtension(reqVO.getName()));
        saveReq.setContent(reqVO.getContent());
        saveReq.setWidthMm(reqVO.getWidthMm());
        saveReq.setHeightMm(reqVO.getHeightMm());
        saveReq.setCategory(reqVO.getCategory());
        saveReq.setKeywords(reqVO.getKeywords());
        return saveReq;
    }

    private AppLabelTemplateRespVO toRespVO(MaxLabelShareContentDO content) {
        AppLabelTemplateRespVO resp = new AppLabelTemplateRespVO();
        copy(resp, content);
        return resp;
    }

    private void copy(AppLabelTemplateRespVO resp, MaxLabelShareContentDO content) {
        resp.setId(content.getId());
        resp.setName(stripTemplateExtension(content.getName()));
        resp.setSizeBytes(content.getSizeBytes() == null ? null : content.getSizeBytes().intValue());
        resp.setWidthMm(content.getWidthMm());
        resp.setHeightMm(content.getHeightMm());
        resp.setCategory(content.getCategory());
        resp.setKeywords(content.getKeywords());
        resp.setShareStatus(null);
        resp.setThumb(MaxLabelTemplateSketch.dataUrl(content.getContent(),
                content.getWidthMm(), content.getHeightMm()));
        resp.setCreateTime(content.getCreateTime());
        resp.setUpdateTime(content.getUpdateTime());
    }

    /** 旧接口名不带扩展名：落库前补 .msdx，返回时再剥掉，保持旧桌面端行为。 */
    private String withTemplateExtension(String name) {
        String value = name == null ? "" : name.trim();
        return value.toLowerCase().endsWith(".msdx") ? value : value + ".msdx";
    }

    private String stripTemplateExtension(String name) {
        if (name == null) {
            return null;
        }
        return name.toLowerCase().endsWith(".msdx")
                ? name.substring(0, name.length() - ".msdx".length()) : name;
    }

}
