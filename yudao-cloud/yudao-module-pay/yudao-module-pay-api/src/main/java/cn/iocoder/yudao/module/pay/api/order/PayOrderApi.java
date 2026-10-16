package cn.iocoder.yudao.module.pay.api.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - 支付单")
public interface PayOrderApi {

    String PREFIX = ApiConstants.PREFIX + "/order";

    @PostMapping(PREFIX + "/create")
    @Operation(summary = "创建支付单")
    CommonResult<Long> createOrder(@Valid @RequestBody PayOrderCreateReqDTO reqDTO);

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "获得支付单")
    @Parameter(name = "id", description = "支付单编号", example = "1", required = true)
    @PermitAll
    CommonResult<PayOrderRespDTO> getOrder(@RequestParam("id") Long id);

    /**
     * 创建一笔**已支付**的对公转账支付单（008）
     *
     * ⚠️ 与 {@link #createOrder} 的区别：那一个是"待支付"，本方法**直接造成已支付** ——
     * 因为对公转账的钱是客户**线下**打进银行账户的，财务确认到账后才调它。
     *
     * ⚠️ 它会**同时建一条支付通知任务** —— 贸易端的 `/trade/order/update-paid` 回调就靠它，
     * 订单因此从「待收款」进入「待发货」。**不要绕过它直接改订单状态**：
     * 退款挂在支付单上，绕开就没有退款链路了。
     */
    @PostMapping(PREFIX + "/create-paid-offline")
    @Operation(summary = "创建已支付的对公转账支付单")
    CommonResult<Long> createPaidOfflineOrder(@Valid @RequestBody PayOrderCreateReqDTO reqDTO);

    @PutMapping(PREFIX + "/update-price")
    @Operation(summary = "更新支付订单价格")
    @Parameters({
            @Parameter(name = "id", description = "支付单编号", example = "1", required = true),
            @Parameter(name = "payPrice", description = "支付单价格", example = "100", required = true)
    })
    CommonResult<Boolean> updatePayOrderPrice(@RequestParam("id") Long id,
                                              @RequestParam("payPrice") Integer payPrice);

}
