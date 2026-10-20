package cn.iocoder.yudao.module.product.controller.admin.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.*;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
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

@Tag(name = "管理后台 - MaxLabel 会员授权商品")
@RestController
@RequestMapping("/product/subscription-plan")
@Validated
public class MaxLabelSubscriptionPlanController {

    @Resource
    private MaxLabelSubscriptionPlanService planService;
    @Resource
    private ProductSpuService spuService;
    @Resource
    private ProductSkuService skuService;

    @PostMapping("/create")
    @Operation(summary = "创建会员授权计划")
    @PreAuthorize("@ss.hasPermission('product:subscription-plan:create')")
    public CommonResult<Long> createPlan(@Valid @RequestBody MaxLabelSubscriptionPlanCreateReqVO reqVO) {
        return success(planService.createPlan(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新会员授权计划")
    @PreAuthorize("@ss.hasPermission('product:subscription-plan:update')")
    public CommonResult<Boolean> updatePlan(@Valid @RequestBody MaxLabelSubscriptionPlanUpdateReqVO reqVO) {
        planService.updatePlan(reqVO);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得会员授权计划")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:subscription-plan:query')")
    public CommonResult<MaxLabelSubscriptionPlanRespVO> getPlan(@RequestParam("id") Long id) {
        return success(toRespVO(planService.getPlan(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得会员授权计划分页")
    @PreAuthorize("@ss.hasPermission('product:subscription-plan:query')")
    public CommonResult<PageResult<MaxLabelSubscriptionPlanRespVO>> getPlanPage(
            @Valid MaxLabelSubscriptionPlanPageReqVO reqVO) {
        PageResult<MaxLabelSubscriptionPlanDO> page = planService.getPlanPage(reqVO);
        List<MaxLabelSubscriptionPlanRespVO> list = page.getList().stream().map(this::toRespVO).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    private MaxLabelSubscriptionPlanRespVO toRespVO(MaxLabelSubscriptionPlanDO plan) {
        if (plan == null) {
            return null;
        }
        MaxLabelSubscriptionPlanRespVO vo = BeanUtils.toBean(plan, MaxLabelSubscriptionPlanRespVO.class);
        ProductSpuDO spu = spuService.getSpu(plan.getSpuId());
        ProductSkuDO sku = skuService.getSku(plan.getSkuId());
        if (spu != null) {
            vo.setSpuName(spu.getName());
        }
        if (sku != null) {
            vo.setSkuName(sku.getProperties() == null || sku.getProperties().isEmpty()
                    ? spu == null ? null : spu.getName()
                    : sku.getProperties().toString());
            vo.setPrice(sku.getPrice());
        }
        return vo;
    }

}
