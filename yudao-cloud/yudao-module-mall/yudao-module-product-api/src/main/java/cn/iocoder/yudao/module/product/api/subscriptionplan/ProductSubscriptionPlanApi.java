package cn.iocoder.yudao.module.product.api.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.api.subscriptionplan.dto.ProductSubscriptionPlanRespDTO;
import cn.iocoder.yudao.module.product.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - MaxLabel 会员授权商品")
public interface ProductSubscriptionPlanApi {

    String PREFIX = ApiConstants.PREFIX + "/subscription-plan";

    @GetMapping(PREFIX + "/get-by-sku-id")
    @Parameter(name = "skuId", description = "商品 SKU 编号", required = true)
    CommonResult<ProductSubscriptionPlanRespDTO> getBySkuId(@RequestParam("skuId") Long skuId);

}
