package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "管理后台 - 代客订单支付状态 Response VO")
@Data
@Accessors(chain = true)
public class AdminAgentOrderPayStatusRespVO {

    @Schema(description = "订单编号", example = "123456")
    private Long orderId;

    @Schema(description = "订单状态", example = "0")
    private Integer orderStatus;

    @Schema(description = "是否已支付", example = "false")
    private Boolean payStatus;

    @Schema(description = "支付单编号；对公转账时为 null", example = "98765")
    private Long payOrderId;

}
