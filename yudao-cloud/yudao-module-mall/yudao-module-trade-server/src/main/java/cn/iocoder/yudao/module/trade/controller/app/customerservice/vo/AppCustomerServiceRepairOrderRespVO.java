package cn.iocoder.yudao.module.trade.controller.app.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 维修订单（嵌套在维修详情中）")
@Data
public class AppCustomerServiceRepairOrderRespVO {

    @Schema(description = "维修订单编号", example = "701")
    private Long id;
    @Schema(description = "维修费，分", example = "10000")
    private Integer repairFee;
    @Schema(description = "寄回运费，分", example = "2000")
    private Integer returnFreight;
    @Schema(description = "应付合计，分", example = "12000")
    private Integer payableAmount;
    @Schema(description = "费用说明")
    private String feeRemark;
    @Schema(description = "支付状态")
    private Integer payStatus;
    @Schema(description = "支付状态名")
    private String payStatusName;
    @Schema(description = "退款状态")
    private Integer refundStatus;
    @Schema(description = "退款状态名")
    private String refundStatusName;

}
