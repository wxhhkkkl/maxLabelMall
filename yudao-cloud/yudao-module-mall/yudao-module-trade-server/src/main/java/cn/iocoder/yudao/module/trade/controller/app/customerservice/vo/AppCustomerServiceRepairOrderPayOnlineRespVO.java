package cn.iocoder.yudao.module.trade.controller.app.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 维修订单在线支付 Response VO")
@Data
public class AppCustomerServiceRepairOrderPayOnlineRespVO {

    @Schema(description = "支付单编号", example = "99001")
    private Long payOrderId;

}
