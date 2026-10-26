package cn.iocoder.yudao.module.product.controller.app.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.product.controller.app.subscriptionplan.vo.AppMaxLabelSubscriptionPlanRespVO;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.ENTERPRISE_PLAN_FORBIDDEN;

/**
 * 用户 App - 企业授权商品（仅企业 OWNER/ADMIN 可见）
 */
@Tag(name = "用户 App - 企业授权商品")
@RestController
@RequestMapping("/product/subscription-plan")
@Validated
public class AppEnterpriseSubscriptionPlanController {

    @Resource
    private MaxLabelSubscriptionPlanService planService;
    @Resource
    private MemberOrgApi memberOrgApi;

    @GetMapping("/enterprise/list")
    @Operation(summary = "获得企业授权商品列表")
    public CommonResult<List<AppMaxLabelSubscriptionPlanRespVO>> getEnterprisePlanList() {
        requireEnterpriseAdmin();
        return success(planService.getEnabledEnterprisePlanListWithProduct());
    }

    @GetMapping("/enterprise/get")
    @Operation(summary = "获得企业授权商品详情")
    @Parameter(name = "skuId", description = "SKU 编号", required = true)
    public CommonResult<AppMaxLabelSubscriptionPlanRespVO> getEnterprisePlanDetail(
            @RequestParam("skuId") Long skuId) {
        requireEnterpriseAdmin();
        return success(planService.getEnterprisePlanDetail(skuId));
    }

    private void requireEnterpriseAdmin() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MemberOrgRespDTO org = userId == null ? null
                : memberOrgApi.getByMemberId(userId).getCheckedData();
        if (org == null || !Integer.valueOf(0).equals(org.getStatus())
                || !(Integer.valueOf(1).equals(org.getRole()) || Integer.valueOf(2).equals(org.getRole()))) {
            throw exception(ENTERPRISE_PLAN_FORBIDDEN);
        }
    }

}
