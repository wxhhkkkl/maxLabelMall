package cn.iocoder.yudao.module.trade.controller.app.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "用户 App - 维修订单支付 Request VO")
@Data
public class AppCustomerServiceRepairOrderPayReqVO {

    @Schema(description = "维修订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "701")
    @NotNull(message = "维修订单编号不能为空")
    private Long id;

}
