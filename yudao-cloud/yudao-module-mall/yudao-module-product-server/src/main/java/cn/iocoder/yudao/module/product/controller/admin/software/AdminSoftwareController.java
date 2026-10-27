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
