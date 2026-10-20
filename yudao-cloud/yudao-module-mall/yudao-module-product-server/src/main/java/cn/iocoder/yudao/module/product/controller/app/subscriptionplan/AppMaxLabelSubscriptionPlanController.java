package cn.iocoder.yudao.module.product.controller.app.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.controller.app.subscriptionplan.vo.AppMaxLabelSubscriptionPlanRespVO;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 会员授权")
@RestController
@RequestMapping("/product/subscription-plan")
@Validated
public class AppMaxLabelSubscriptionPlanController {

    @Resource
    private MaxLabelSubscriptionPlanService planService;

    @GetMapping("/list")
    @PermitAll
    @Operation(summary = "获得可售会员授权周期列表")
    public CommonResult<List<AppMaxLabelSubscriptionPlanRespVO>> getPlanList() {
        return success(planService.getEnabledPlanListWithProduct());
    }

}
