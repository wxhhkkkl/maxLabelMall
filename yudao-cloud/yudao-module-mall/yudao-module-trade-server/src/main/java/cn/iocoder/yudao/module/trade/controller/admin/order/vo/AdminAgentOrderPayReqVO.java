package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 代客订单发起支付 Request VO")
@Data
public class AdminAgentOrderPayReqVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotNull(message = "订单编号不能为空")
    private Long orderId;

    @Schema(description = "支付渠道：wx_native / alipay_pc", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "wx_native")
    @NotBlank(message = "支付渠道不能为空")
    private String channelCode;

}
