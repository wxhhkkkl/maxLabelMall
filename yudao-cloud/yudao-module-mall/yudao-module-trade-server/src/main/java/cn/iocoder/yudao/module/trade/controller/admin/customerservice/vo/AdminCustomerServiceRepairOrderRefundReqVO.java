package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 维修订单退款 Request VO")
@Data
public class AdminCustomerServiceRepairOrderRefundReqVO {

    @Schema(description = "维修订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "701")
    @NotNull(message = "维修订单编号不能为空")
    private Long id;

    @Schema(description = "退款原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "退款原因不能为空")
    @Size(max = 200, message = "退款原因不能超过 200 字")
    private String reason;

}
