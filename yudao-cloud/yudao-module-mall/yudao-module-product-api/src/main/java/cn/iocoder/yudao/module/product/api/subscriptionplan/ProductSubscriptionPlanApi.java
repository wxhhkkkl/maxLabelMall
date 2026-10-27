package cn.iocoder.yudao.module.product.api.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.api.subscriptionplan.dto.ProductSubscriptionPlanRespDTO;
import cn.iocoder.yudao.module.product.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - MaxLabel 会员授权商品")
public interface ProductSubscriptionPlanApi {

    String PREFIX = ApiConstants.PREFIX + "/subscription-plan";

    @GetMapping(PREFIX + "/get-by-sku-id")
    @Parameter(name = "skuId", description = "商品 SKU 编号", required = true)
    CommonResult<ProductSubscriptionPlanRespDTO> getBySkuId(@RequestParam("skuId") Long skuId);

    /**
     * 给定 SPU 中，被会员授权计划引用的那些 SPU 编号。
     * 这类商品只能从个人续费页 / 企业采购页进入，**不进入公开商城**（FR-328），
     * 促销活动保存与公开渲染都要用它做校验。
     */
    @GetMapping(PREFIX + "/list-plan-spu-ids")
    @Parameter(name = "spuIds", description = "SPU 编号列表", required = true, example = "1,3,5")
    CommonResult<List<Long>> getPlanSpuIds(@RequestParam("spuIds") Collection<Long> spuIds);

}
