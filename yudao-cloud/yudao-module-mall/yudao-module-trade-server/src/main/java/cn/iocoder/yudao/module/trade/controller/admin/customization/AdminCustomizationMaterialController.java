package cn.iocoder.yudao.module.trade.controller.admin.customization;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.admin.customization.vo.AdminCustomizationAreaRuleSaveReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customization.vo.AdminCustomizationMaterialPageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customization.vo.AdminCustomizationMaterialRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.customization.vo.AdminCustomizationMaterialSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationAreaRuleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationMaterialDO;
import cn.iocoder.yudao.module.trade.service.customization.CustomizationMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 印刷定制材质（014 / US50）
 */
@Tag(name = "管理后台 - 印刷定制材质")
@RestController
@RequestMapping("/trade/customization/material")
@Validated
public class AdminCustomizationMaterialController {

    @Resource
    private CustomizationMaterialService customizationMaterialService;

    @GetMapping("/page")
    @Operation(summary = "获得材质分页")
    @PreAuthorize("@ss.hasPermission('trade:customization-material:query')")
    public CommonResult<PageResult<AdminCustomizationMaterialRespVO>> getMaterialPage(
            @Valid AdminCustomizationMaterialPageReqVO pageReqVO) {
        PageResult<CustomizationMaterialDO> pageResult = customizationMaterialService.getMaterialPage(
                pageReqVO.getName(), pageReqVO.getStatus(), pageReqVO);
        return success(new PageResult<>(buildRespList(pageResult.getList()), pageResult.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得材质")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('trade:customization-material:query')")
    public CommonResult<AdminCustomizationMaterialRespVO> getMaterial(@RequestParam("id") Long id) {
        CustomizationMaterialDO material = customizationMaterialService.getMaterial(id);
        return success(material == null ? null : buildResp(material));
    }

    @PostMapping("/create")
    @Operation(summary = "创建材质")
    @PreAuthorize("@ss.hasPermission('trade:customization-material:create')")
    public CommonResult<Long> createMaterial(@Valid @RequestBody AdminCustomizationMaterialSaveReqVO reqVO) {
        return success(customizationMaterialService.createMaterial(
                BeanUtils.toBean(reqVO, CustomizationMaterialDO.class), buildRules(reqVO.getAreaRules())));
    }

    @PutMapping("/update")
    @Operation(summary = "修改材质")
    @PreAuthorize("@ss.hasPermission('trade:customization-material:update')")
    public CommonResult<Boolean> updateMaterial(@Valid @RequestBody AdminCustomizationMaterialSaveReqVO reqVO) {
        customizationMaterialService.updateMaterial(
                BeanUtils.toBean(reqVO, CustomizationMaterialDO.class), buildRules(reqVO.getAreaRules()));
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用 / 停用材质")
    @PreAuthorize("@ss.hasPermission('trade:customization-material:update')")
    public CommonResult<Boolean> updateMaterialStatus(@RequestParam("id") Long id,
                                                      @RequestParam("status") Integer status) {
        customizationMaterialService.updateMaterialStatus(id, status);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除材质")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('trade:customization-material:delete')")
    public CommonResult<Boolean> deleteMaterial(@RequestParam("id") Long id) {
        customizationMaterialService.deleteMaterial(id);
        return success(true);
    }

    private List<AdminCustomizationMaterialRespVO> buildRespList(List<CustomizationMaterialDO> list) {
        List<AdminCustomizationMaterialRespVO> result = new ArrayList<>(list.size());
        list.forEach(material -> result.add(buildResp(material)));
        return result;
    }

    private AdminCustomizationMaterialRespVO buildResp(CustomizationMaterialDO material) {
        AdminCustomizationMaterialRespVO vo = BeanUtils.toBean(material, AdminCustomizationMaterialRespVO.class);
        vo.setAreaRules(BeanUtils.toBean(customizationMaterialService.getAreaRules(material.getId()),
                AdminCustomizationMaterialRespVO.AreaRule.class));
        return vo;
    }

    private static List<CustomizationAreaRuleDO> buildRules(List<AdminCustomizationAreaRuleSaveReqVO> rules) {
        return BeanUtils.toBean(rules, CustomizationAreaRuleDO.class);
    }

}
