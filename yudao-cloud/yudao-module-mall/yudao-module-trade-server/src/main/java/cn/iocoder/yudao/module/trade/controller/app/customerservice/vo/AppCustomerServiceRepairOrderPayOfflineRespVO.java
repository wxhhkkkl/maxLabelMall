package cn.iocoder.yudao.module.trade.controller.app.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 维修订单对公转账 Response VO")
@Data
public class AppCustomerServiceRepairOrderPayOfflineRespVO {

    @Schema(description = "支付状态：10 待确认收款", example = "10")
    private Integer payStatus;
    @Schema(description = "收款信息")
    private AppCustomerServiceOfflinePayInfoVO offline;

}
