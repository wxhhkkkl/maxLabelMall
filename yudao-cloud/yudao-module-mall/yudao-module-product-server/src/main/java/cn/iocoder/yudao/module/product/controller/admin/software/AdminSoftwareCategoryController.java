package cn.iocoder.yudao.module.product.controller.admin.software;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.*;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;
import cn.iocoder.yudao.module.product.service.software.SoftwareCategoryService;
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

/**
 * 管理后台 - 软件分类（013 / US50）
 */
@Tag(name = "管理后台 - 软件分类")
@RestController
@RequestMapping("/software/category")
@Validated
public class AdminSoftwareCategoryController {

    @Resource
    private SoftwareCategoryService categoryService;

    @GetMapping("/page")
    @Operation(summary = "获得分类分页")
    @PreAuthorize("@ss.hasPermission('software:category:query')")
    public CommonResult<PageResult<AdminSoftwareCategoryRespVO>> getCategoryPage(@Valid AdminSoftwareCategoryPageReqVO pageReqVO) {
        PageResult<SoftwareCategoryDO> pageResult = categoryService.getCategoryPage(
                pageReqVO.getName(), pageReqVO.getStatus(), pageReqVO);
        return success(BeanUtils.toBean(pageResult, AdminSoftwareCategoryRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得分类列表（下拉用）")
    @PreAuthorize("@ss.hasPermission('software:category:query')")
    public CommonResult<List<AdminSoftwareCategoryRespVO>> getCategoryList() {
        return success(BeanUtils.toBean(categoryService.getEnabledCategoryList(), AdminSoftwareCategoryRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建分类")
    @PreAuthorize("@ss.hasPermission('software:category:create')")
    public CommonResult<Long> createCategory(@Valid @RequestBody AdminSoftwareCategorySaveReqVO createReqVO) {
        return success(categoryService.createCategory(BeanUtils.toBean(createReqVO, SoftwareCategoryDO.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "修改分类")
    @PreAuthorize("@ss.hasPermission('software:category:update')")
    public CommonResult<Boolean> updateCategory(@Valid @RequestBody AdminSoftwareCategorySaveReqVO updateReqVO) {
        categoryService.updateCategory(BeanUtils.toBean(updateReqVO, SoftwareCategoryDO.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除分类（分类下仍有软件时拒绝）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('software:category:delete')")
    public CommonResult<Boolean> deleteCategory(@RequestParam("id") Long id) {
        categoryService.deleteCategory(id);
        return success(true);
    }

}
