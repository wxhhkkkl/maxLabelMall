package cn.iocoder.yudao.module.product.controller.app.software;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.app.software.vo.*;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import cn.iocoder.yudao.module.product.service.software.SoftwareCategoryService;
import cn.iocoder.yudao.module.product.service.software.SoftwareService;
import cn.iocoder.yudao.module.product.service.software.SoftwareVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_NOT_EXISTS;

/**
 * 用户 App - 软件与驱动（013 / US49）
 *
 * 全部公开免登录（FR-342）；只暴露「当前发布」，历史版本不对外（Q84）。
 */
@Tag(name = "用户 App - 软件与驱动")
@RestController
@RequestMapping("/software")
@Validated
public class AppSoftwareController {

    @Resource
    private SoftwareCategoryService categoryService;
    @Resource
    private SoftwareService softwareService;
    @Resource
    private SoftwareVersionService versionService;

    @GetMapping("/category/list")
    @Operation(summary = "服务支持页：有可展示软件的分类")
    @PermitAll
    public CommonResult<List<AppSoftwareCategoryRespVO>> getCategoryList() {
        List<SoftwareCategoryDO> categories = categoryService.getEnabledCategoryList();
        if (categories.isEmpty()) {
            return success(Collections.emptyList());
        }
        Set<Long> withSoftware = softwareService
                .getSupportSoftwareList(categories.stream().map(SoftwareCategoryDO::getId).toList())
                .stream().map(SoftwareDO::getCategoryId).collect(Collectors.toSet());
        List<SoftwareCategoryDO> visible = categories.stream()
                .filter(c -> withSoftware.contains(c.getId())).toList();
        return success(BeanUtils.toBean(visible, AppSoftwareCategoryRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "服务支持页：某分类下可展示的软件")
    @Parameter(name = "categoryId", description = "分类编号", required = true)
    @PermitAll
    public CommonResult<List<AppSoftwareRespVO>> getSoftwareList(@RequestParam("categoryId") Long categoryId) {
        List<SoftwareDO> softwares = softwareService.getSupportSoftwareList(List.of(categoryId));
        if (softwares.isEmpty()) {
            return success(Collections.emptyList());
        }
        Map<Long, List<SoftwareVersionDO>> versionMap = versionService
                .getCurrentVersionList(softwares.stream().map(SoftwareDO::getId).toList())
                .stream().collect(Collectors.groupingBy(SoftwareVersionDO::getSoftwareId));
        return success(softwares.stream().map(s -> {
            AppSoftwareRespVO vo = BeanUtils.toBean(s, AppSoftwareRespVO.class);
            List<SoftwareVersionDO> versions = versionMap.getOrDefault(s.getId(), Collections.emptyList());
            if (!versions.isEmpty()) {
                vo.setLatestVersion(versions.get(0).getVersion());
                vo.setPlatforms(versions.stream().map(SoftwareVersionDO::getPlatform).distinct().toList());
            }
            return vo;
        }).toList());
    }

    @GetMapping("/get")
    @Operation(summary = "服务支持页：软件详情（只含当前发布版本）")
    @Parameter(name = "id", description = "软件编号", required = true)
    @PermitAll
    public CommonResult<AppSoftwareDetailRespVO> getSoftware(@RequestParam("id") Long id) {
        SoftwareDO software = softwareService.getSoftware(id);
        // 不存在 / 停用 / 不对外展示 统一按「不存在」处理，避免枚举（contracts/app-api §1.3）
        if (software == null
                || !Integer.valueOf(0).equals(software.getStatus())
                || !Boolean.TRUE.equals(software.getShowInSupport())) {
            throw exception(SOFTWARE_NOT_EXISTS);
        }
        AppSoftwareDetailRespVO detail = BeanUtils.toBean(software, AppSoftwareDetailRespVO.class);
        List<SoftwareVersionDO> versions = versionService.getCurrentVersionList(List.of(id));
        detail.setVersions(versions.stream().map(v -> {
            AppSoftwareVersionRespVO vo = BeanUtils.toBean(v, AppSoftwareVersionRespVO.class);
            vo.setDownloadUrl(v.getFileUrl());
            return vo;
        }).toList());
        if (!versions.isEmpty()) {
            detail.setLatestVersion(versions.get(0).getVersion());
            detail.setPlatforms(versions.stream().map(SoftwareVersionDO::getPlatform).distinct().toList());
        }
        return success(detail);
    }

}
