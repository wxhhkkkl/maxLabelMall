package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 确认维修对公收款 Request VO")
@Data
public class AdminCustomerServiceRepairOrderConfirmOfflineReqVO {

    @Schema(description = "维修订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "701")
    @NotNull(message = "维修订单编号不能为空")
    private Long id;

    @Schema(description = "银行流水号")
    private String bankSerialNo;
    @Schema(description = "付款人")
    private String payerName;
    @Schema(description = "到账时间")
    private LocalDateTime paidTime;
    @Schema(description = "备注")
    private String remark;

}
