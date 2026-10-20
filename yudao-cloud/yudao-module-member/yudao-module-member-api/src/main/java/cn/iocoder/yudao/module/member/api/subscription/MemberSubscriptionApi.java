package cn.iocoder.yudao.module.member.api.subscription;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.subscription.dto.MemberSubscriptionGrantReqDTO;
import cn.iocoder.yudao.module.member.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - MaxLabel 会员订阅")
public interface MemberSubscriptionApi {

    String PREFIX = ApiConstants.PREFIX + "/subscription";

    @PostMapping(PREFIX + "/grant")
    @Operation(summary = "发放/顺延会员订阅（幂等）")
    CommonResult<Boolean> grant(@RequestBody MemberSubscriptionGrantReqDTO reqDTO);

    @PostMapping(PREFIX + "/mark-refunded-by-order")
    @Operation(summary = "标记订单来源的订阅已退款")
    @Parameter(name = "orderId", required = true)
    CommonResult<Boolean> markRefundedByOrder(@RequestParam("orderId") Long orderId);

    @PostMapping(PREFIX + "/mark-refunded-by-order-item")
    @Operation(summary = "标记订单项来源的订阅已退款")
    @Parameter(name = "orderItemId", required = true)
    CommonResult<Boolean> markRefundedByOrderItem(@RequestParam("orderItemId") Long orderItemId);

}
