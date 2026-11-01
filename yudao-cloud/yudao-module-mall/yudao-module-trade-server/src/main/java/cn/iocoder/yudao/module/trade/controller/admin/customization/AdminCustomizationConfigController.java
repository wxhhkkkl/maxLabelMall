package cn.iocoder.yudao.module.trade.controller.admin.customization;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.admin.customization.vo.AdminCustomizationConfigSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationConfigDO;
import cn.iocoder.yudao.module.trade.service.customization.CustomizationConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 印刷定制计价参数（014 / US50）
 */
@Tag(name = "管理后台 - 印刷定制计价参数")
@RestController
@RequestMapping("/trade/customization/config")
@Validated
public class AdminCustomizationConfigController {

    @Resource
    private CustomizationConfigService customizationConfigService;

    @GetMapping("/get")
    @Operation(summary = "获得计价参数")
    @PreAuthorize("@ss.hasPermission('trade:customization-config:query')")
    public CommonResult<AdminCustomizationConfigSaveReqVO> getConfig() {
        CustomizationConfigDO config = customizationConfigService.getConfigOrDefault();
        return success(BeanUtils.toBean(config, AdminCustomizationConfigSaveReqVO.class));
    }

    @PutMapping("/save")
    @Operation(summary = "保存计价参数")
    @PreAuthorize("@ss.hasPermission('trade:customization-config:update')")
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody AdminCustomizationConfigSaveReqVO reqVO) {
        customizationConfigService.saveConfig(BeanUtils.toBean(reqVO, CustomizationConfigDO.class));
        return success(true);
    }

}
