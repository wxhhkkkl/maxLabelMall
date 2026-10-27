package cn.iocoder.yudao.module.product.controller.admin.software;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.AdminSoftwareVersionPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.AdminSoftwareVersionRegisterReqVO;
import cn.iocoder.yudao.module.product.controller.admin.software.vo.AdminSoftwareVersionRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
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
 * 管理后台 - 软件版本（013）
 *
 * 上传走 infra 既有预签名端点（`GET /admin-api/infra/file/presigned-url`），这里只负责登记与发布。
 */
@Tag(name = "管理后台 - 软件版本")
@RestController
@RequestMapping("/software/version")
@Validated
public class AdminSoftwareVersionController {

    @Resource
    private SoftwareVersionService versionService;

    @GetMapping("/page")
    @Operation(summary = "获得版本分页（含历史与已下架）")
    @PreAuthorize("@ss.hasPermission('software:version:query')")
    public CommonResult<PageResult<AdminSoftwareVersionRespVO>> getVersionPage(@Valid AdminSoftwareVersionPageReqVO pageReqVO) {
        PageResult<SoftwareVersionDO> pageResult = versionService.getVersionPage(pageReqVO.getSoftwareId(), pageReqVO);
        List<AdminSoftwareVersionRespVO> list = pageResult.getList().stream().map(this::toRespVO).toList();
        return success(new PageResult<>(list, pageResult.getTotal()));
    }

    private AdminSoftwareVersionRespVO toRespVO(SoftwareVersionDO source) {
        AdminSoftwareVersionRespVO target = BeanUtils.toBean(source, AdminSoftwareVersionRespVO.class);
        target.setCurrent(Integer.valueOf(1).equals(source.getCurrentFlag()));
        return target;
    }

    @PostMapping("/register")
    @Operation(summary = "登记版本（草稿）")
    @PreAuthorize("@ss.hasPermission('software:version:create')")
    public CommonResult<Long> registerVersion(@Valid @RequestBody AdminSoftwareVersionRegisterReqVO reqVO) {
        return success(versionService.registerVersion(BeanUtils.toBean(reqVO, SoftwareVersionDO.class)));
    }

    @PostMapping("/publish")
    @Operation(summary = "发布版本（current=true 时同时设为当前发布）")
    @Parameter(name = "id", description = "版本编号", required = true)
    @PreAuthorize("@ss.hasPermission('software:version:publish')")
    public CommonResult<Boolean> publishVersion(@RequestParam("id") Long id,
                                                @RequestParam(value = "current", required = false, defaultValue = "false") boolean current) {
        versionService.publishVersion(id, current);
        return success(true);
    }

    @PutMapping("/unpublish")
    @Operation(summary = "下架版本")
    @Parameter(name = "id", description = "版本编号", required = true)
    @PreAuthorize("@ss.hasPermission('software:version:publish')")
    public CommonResult<Boolean> unpublishVersion(@RequestParam("id") Long id) {
        versionService.unpublishVersion(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除版本（软删除）")
    @Parameter(name = "id", description = "版本编号", required = true)
    @PreAuthorize("@ss.hasPermission('software:version:delete')")
    public CommonResult<Boolean> deleteVersion(@RequestParam("id") Long id) {
        versionService.deleteVersion(id);
        return success(true);
    }

}
