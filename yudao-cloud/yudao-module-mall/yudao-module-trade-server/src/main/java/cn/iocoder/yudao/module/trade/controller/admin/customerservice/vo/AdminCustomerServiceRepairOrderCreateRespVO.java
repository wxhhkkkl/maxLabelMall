package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 生成维修订单 Response VO")
@Data
public class AdminCustomerServiceRepairOrderCreateRespVO {

    @Schema(description = "维修订单编号", example = "701")
    private Long repairOrderId;
    @Schema(description = "应付合计，分", example = "12000")
    private Integer payableAmount;
    @Schema(description = "支付状态：0 待支付 30 无需支付", example = "0")
    private Integer payStatus;

}
