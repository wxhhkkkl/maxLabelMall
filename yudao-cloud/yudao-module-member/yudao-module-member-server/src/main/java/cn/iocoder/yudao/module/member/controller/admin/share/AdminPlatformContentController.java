package cn.iocoder.yudao.module.member.controller.admin.share;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentCategoryRespVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentCategorySaveReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentDetailRespVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentRespVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentSaveReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentStatusReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentUploadReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentDO;
import cn.iocoder.yudao.module.member.service.share.PlatformContentCategoryService;
import cn.iocoder.yudao.module.member.service.share.PlatformContentService;
import cn.iocoder.yudao.module.member.service.template.MaxLabelTemplateSketch;
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

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MaxLabel 平台共享内容")
@RestController
@RequestMapping("/member/platform-content")
@Validated
public class AdminPlatformContentController {

    @Resource
    private PlatformContentService platformContentService;
    @Resource
    private PlatformContentCategoryService categoryService;

    @GetMapping("/page")
    @Operation(summary = "平台共享内容分页")
    @PreAuthorize("@ss.hasPermission('member:platform-content:query')")
    public CommonResult<PageResult<AdminPlatformContentRespVO>> getPage(
            @Valid AdminPlatformContentPageReqVO reqVO) {
        PageResult<MaxLabelPlatformContentDO> page = platformContentService.getAdminPage(reqVO);
        List<AdminPlatformContentRespVO> list = page.getList().stream().map(this::toResp).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "平台共享内容详情")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('member:platform-content:query')")
    public CommonResult<AdminPlatformContentDetailRespVO> get(@RequestParam("id") Long id) {
        MaxLabelPlatformContentDO content = platformContentService.getAdmin(id);
        AdminPlatformContentDetailRespVO resp = BeanUtils.toBean(content, AdminPlatformContentDetailRespVO.class);
        resp.setThumb(thumb(content));
        resp.setDownloadUrl(platformContentService.getDownloadUrl(content));
        return success(resp);
    }

    @PostMapping("/upload")
    @Operation(summary = "上传平台共享内容")
    @PreAuthorize("@ss.hasPermission('member:platform-content:create')")
    public CommonResult<Long> upload(@Valid AdminPlatformContentUploadReqVO reqVO) throws Exception {
        return success(platformContentService.upload(SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "新建平台共享内容（文本正文）")
    @PreAuthorize("@ss.hasPermission('member:platform-content:create')")
    public CommonResult<Long> create(@Valid @RequestBody AdminPlatformContentSaveReqVO reqVO) {
        return success(platformContentService.create(SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "编辑平台共享内容")
    @PreAuthorize("@ss.hasPermission('member:platform-content:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody AdminPlatformContentSaveReqVO reqVO) {
        platformContentService.update(SecurityFrameworkUtils.getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "平台共享内容上下架")
    @PreAuthorize("@ss.hasPermission('member:platform-content:update')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody AdminPlatformContentStatusReqVO reqVO) {
        platformContentService.updateStatus(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除平台共享内容")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('member:platform-content:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        platformContentService.delete(id);
        return success(true);
    }

    @GetMapping("/category/list")
    @Operation(summary = "平台共享内容分类列表")
    @PreAuthorize("@ss.hasPermission('member:platform-content:query')")
    public CommonResult<List<AdminPlatformContentCategoryRespVO>> getCategoryList() {
        List<AdminPlatformContentCategoryRespVO> list = categoryService.getList().stream()
                .map(category -> BeanUtils.toBean(category, AdminPlatformContentCategoryRespVO.class))
                .toList();
        return success(list);
    }

    @PostMapping("/category/create")
    @Operation(summary = "新增平台共享内容分类")
    @PreAuthorize("@ss.hasPermission('member:platform-content:update')")
    public CommonResult<Long> createCategory(@Valid @RequestBody AdminPlatformContentCategorySaveReqVO reqVO) {
        return success(categoryService.create(reqVO.getName(), reqVO.getSort()));
    }

    @DeleteMapping("/category/delete")
    @Operation(summary = "删除平台共享内容分类")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('member:platform-content:update')")
    public CommonResult<Boolean> deleteCategory(@RequestParam("id") Long id) {
        categoryService.delete(id);
        return success(true);
    }

    private AdminPlatformContentRespVO toResp(MaxLabelPlatformContentDO content) {
        AdminPlatformContentRespVO resp = BeanUtils.toBean(content, AdminPlatformContentRespVO.class);
        resp.setThumb(thumb(content));
        return resp;
    }

    private String thumb(MaxLabelPlatformContentDO content) {
        return Objects.equals(content.getContentType(), 1) && content.getContent() != null
                ? MaxLabelTemplateSketch.dataUrl(content.getContent(), content.getWidthMm(), content.getHeightMm())
                : null;
    }

}
