package cn.iocoder.yudao.module.member.controller.admin.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminLabelTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.service.share.ShareContentService;
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
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_TEMPLATE_NOT_FOUND;

@Tag(name = "管理后台 - MaxLabel 会员模板")
@RestController
@RequestMapping("/member/cloud-template")
@Validated
public class AdminLabelTemplateController {

    @Resource
    private ShareContentService contentService;
    @Resource
    private MaxLabelShareContentMapper contentMapper;

    @GetMapping("/page")
    @Operation(summary = "获得会员云端模板分页")
    @PreAuthorize("@ss.hasPermission('member:cloud-template:query')")
    public CommonResult<PageResult<AppLabelTemplateRespVO>> getPage(@Valid AdminLabelTemplatePageReqVO reqVO) {
        AppShareContentPageReqVO pageReq = new AppShareContentPageReqVO();
        pageReq.setScope(1);
        pageReq.setContentType(1);
        pageReq.setKeyword(reqVO.getKeyword());
        pageReq.setPageNo(reqVO.getPageNo());
        pageReq.setPageSize(reqVO.getPageSize());
        PageResult<MaxLabelShareContentDO> page = contentService.getPage(reqVO.getUserId(), pageReq);
        List<AppLabelTemplateRespVO> list = page.getList().stream().map(this::toRespVO).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得会员云端模板正文（后台预览/下载）")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('member:cloud-template:query')")
    public CommonResult<AppLabelTemplateContentRespVO> get(@RequestParam("id") Long id) {
        MaxLabelShareContentDO content = contentMapper.selectById(id);
        if (content == null || !Objects.equals(content.getScope(), 1)
                || !Objects.equals(content.getContentType(), 1)) {
            throw exception(MEMBER_TEMPLATE_NOT_FOUND);
        }
        AppLabelTemplateContentRespVO resp = new AppLabelTemplateContentRespVO();
        copy(resp, content);
        resp.setContent(content.getContent());
        return success(resp);
    }

    private AppLabelTemplateRespVO toRespVO(MaxLabelShareContentDO content) {
        AppLabelTemplateRespVO resp = new AppLabelTemplateRespVO();
        copy(resp, content);
        return resp;
    }

    private void copy(AppLabelTemplateRespVO resp, MaxLabelShareContentDO content) {
        resp.setId(content.getId());
        resp.setName(content.getName());
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

}
