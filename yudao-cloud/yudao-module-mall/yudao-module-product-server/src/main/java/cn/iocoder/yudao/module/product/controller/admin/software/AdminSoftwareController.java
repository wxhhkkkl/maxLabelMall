package cn.iocoder.yudao.module.product.controller.admin.software;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.*;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import cn.iocoder.yudao.module.product.service.software.SoftwareService;
import cn.iocoder.yudao.module.product.service.software.SoftwareVersionService;
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
 * 管理后台 - 软件 / 驱动（013）
 */
@Tag(name = "管理后台 - 软件分发")
@RestController
@RequestMapping("/software/info")
@Validated
public class AdminSoftwareController {

    @Resource
    private SoftwareService softwareService;
    @Resource
    private SoftwareVersionService versionService;

    @GetMapping("/page")
    @Operation(summary = "获得软件分页")
    @PreAuthorize("@ss.hasPermission('software:info:query')")
    public CommonResult<PageResult<AdminSoftwareRespVO>> getSoftwarePage(@Valid AdminSoftwarePageReqVO pageReqVO) {
        PageResult<SoftwareDO> pageResult = softwareService.getSoftwarePage(pageReqVO.getCategoryId(),
                pageReqVO.getType(), pageReqVO.getStatus(), pageReqVO.getTitle(), pageReqVO);
        return success(BeanUtils.toBean(pageResult, AdminSoftwareRespVO.class));
    }

    /**
     * 商品「其他设置 → 关联驱动下载」的选项（2026-11-18）
     *
     * ⚠️ 权限故意用 `product:spu:query` 而不是 `software:info:query` —— 选驱动是**编辑商品**的
     * 前置动作，商品运营不一定被授予「软件分发」的查询权限。
     */
    @GetMapping("/simple-list")
    @Operation(summary = "获得软件/驱动精简列表（商品关联驱动用）")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<List<AdminSoftwareSimpleRespVO>> getSimpleList() {
        return success(BeanUtils.toBean(softwareService.getSimpleList(), AdminSoftwareSimpleRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得软件详情（含全部版本）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('software:info:query')")
    public CommonResult<AdminSoftwareDetailRespVO> getSoftware(@RequestParam("id") Long id) {
        SoftwareDO software = softwareService.getSoftware(id);
        AdminSoftwareDetailRespVO respVO = BeanUtils.toBean(software, AdminSoftwareDetailRespVO.class);
        if (respVO == null) {
            return success(null);
        }
        List<SoftwareVersionDO> versions = versionService.getVersionPage(id,
                new PageParam().setPageNo(1).setPageSize(PageParam.PAGE_SIZE_NONE)).getList();
        respVO.setVersions(BeanUtils.toBean(versions, AdminSoftwareVersionRespVO.class));
        return success(respVO);
    }

    @PostMapping("/create")
    @Operation(summary = "创建软件")
    @PreAuthorize("@ss.hasPermission('software:info:create')")
    public CommonResult<Long> createSoftware(@Valid @RequestBody AdminSoftwareSaveReqVO createReqVO) {
        return success(softwareService.createSoftware(BeanUtils.toBean(createReqVO, SoftwareDO.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "修改软件")
    @PreAuthorize("@ss.hasPermission('software:info:update')")
    public CommonResult<Boolean> updateSoftware(@Valid @RequestBody AdminSoftwareSaveReqVO updateReqVO) {
        softwareService.updateSoftware(BeanUtils.toBean(updateReqVO, SoftwareDO.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除软件（软删除）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('software:info:delete')")
    public CommonResult<Boolean> deleteSoftware(@RequestParam("id") Long id) {
        softwareService.deleteSoftware(id);
        return success(true);
    }

}
