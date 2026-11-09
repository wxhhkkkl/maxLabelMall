package cn.iocoder.yudao.module.member.controller.app.share;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppPlatformContentDetailRespVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppPlatformContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppPlatformContentRespVO;
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

@Tag(name = "用户 App - MaxLabel 平台共享内容")
@RestController
@RequestMapping("/member/platform-content")
@Validated
public class AppPlatformContentController {

    @Resource
    private PlatformContentService platformContentService;

    @GetMapping("/page")
    @Operation(summary = "获得平台共享内容分页")
    public CommonResult<PageResult<AppPlatformContentRespVO>> getPage(@Valid AppPlatformContentPageReqVO reqVO) {
        PageResult<MaxLabelPlatformContentDO> page = platformContentService.getOnlinePage(reqVO);
        List<AppPlatformContentRespVO> list = page.getList().stream().map(this::toResp).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得平台共享内容详情")
    @Parameter(name = "id", required = true)
    public CommonResult<AppPlatformContentDetailRespVO> get(@RequestParam("id") Long id) {
        MaxLabelPlatformContentDO content = platformContentService.getOnline(id);
        AppPlatformContentDetailRespVO resp = BeanUtils.toBean(content, AppPlatformContentDetailRespVO.class);
        resp.setDownloadUrl(platformContentService.getDownloadUrl(content));
        resp.setThumb(thumb(content));
        return success(resp);
    }

    private AppPlatformContentRespVO toResp(MaxLabelPlatformContentDO content) {
        AppPlatformContentRespVO resp = BeanUtils.toBean(content, AppPlatformContentRespVO.class);
        resp.setThumb(thumb(content));
        return resp;
    }

    private String thumb(MaxLabelPlatformContentDO content) {
        if (!Objects.equals(content.getContentType(), 1) || content.getContent() == null) {
            return null;
        }
        return MaxLabelTemplateSketch.dataUrl(content.getContent(), content.getWidthMm(), content.getHeightMm());
    }

}
