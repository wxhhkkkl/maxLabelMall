package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "管理后台 - 代客下单创建 Response VO")
@Data
@Accessors(chain = true)
public class TradeOrderCreateRespVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    private Long orderId;

    @Schema(description = "支付单编号；对公转账时为 null", example = "98765")
    private Long payOrderId;

    @Schema(description = "应付金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "2600")
    private Integer payPrice;

    @Schema(description = "订单状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer orderStatus;

}
