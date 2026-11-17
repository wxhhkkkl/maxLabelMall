package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 维修订单 Response VO")
@Data
public class AdminCustomerServiceRepairOrderRespVO {

    @Schema(description = "维修订单编号", example = "701")
    private Long id;
    @Schema(description = "维修申请编号", example = "601")
    private Long repairRequestId;
    @Schema(description = "会员编号", example = "88")
    private Long memberId;
    @Schema(description = "会员昵称")
    private String memberNickname;
    @Schema(description = "会员手机号")
    private String memberMobile;
    @Schema(description = "设备型号")
    private String deviceModel;
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
    @Schema(description = "支付渠道")
    private String payChannelCode;
    @Schema(description = "支付单号")
    private Long payOrderId;
    @Schema(description = "支付时间")
    private LocalDateTime payTime;
    @Schema(description = "对公确认人")
    private Long offlineConfirmUserId;
    @Schema(description = "对公确认时间")
    private LocalDateTime offlineConfirmTime;
    @Schema(description = "退款状态")
    private Integer refundStatus;
    @Schema(description = "退款状态名")
    private String refundStatusName;
    @Schema(description = "支付退款单号")
    private Long payRefundId;
    @Schema(description = "退款金额，分")
    private Integer refundPrice;
    @Schema(description = "退款原因")
    private String refundReason;
    @Schema(description = "退款完成时间")
    private LocalDateTime refundTime;
    @Schema(description = "退款操作人")
    private Long refundOperatorId;
    @Schema(description = "完成维修时间")
    private LocalDateTime completeTime;
    @Schema(description = "完成维修操作人")
    private Long completeOperatorId;
    @Schema(description = "完成维修操作人昵称")
    private String completeOperatorName;
    @Schema(description = "关联维修申请状态")
    private Integer repairStatus;
    @Schema(description = "关联维修申请状态名")
    private String repairStatusName;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
