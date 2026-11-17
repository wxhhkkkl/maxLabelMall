package cn.iocoder.yudao.module.trade.controller.app.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户 App - 维修申请 Response VO")
@Data
public class AppCustomerServiceRepairRespVO {

    @Schema(description = "维修申请编号", example = "601")
    private Long id;
    @Schema(description = "设备型号")
    private String deviceModel;
    @Schema(description = "故障描述")
    private String faultDesc;
    @Schema(description = "联系手机号")
    private String contactMobile;
    @Schema(description = "返修收件人")
    private String receiverName;
    @Schema(description = "返修联系电话")
    private String receiverMobile;
    @Schema(description = "返修省市区")
    private String receiverArea;
    @Schema(description = "返修详细地址")
    private String receiverDetail;
    @Schema(description = "图片 URL 数组")
    private List<String> images;
    @Schema(description = "状态：0 待联系 10 已联系 20 已开单 30 已寄出 40 维修中 50 已完成 60 已取消")
    private Integer status;
    @Schema(description = "状态名")
    private String statusName;
    @Schema(description = "电话联系时间")
    private LocalDateTime contactTime;
    @Schema(description = "联系结果说明")
    private String contactResult;
    @Schema(description = "客户寄出快递公司")
    private String shipCompany;
    @Schema(description = "客户寄出运单号")
    private String shipNo;
    @Schema(description = "客户标记寄出时间")
    private LocalDateTime shipTime;
    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    // ========== 维修订单（未开单时为 null） ==========
    @Schema(description = "维修订单编号")
    private Long repairOrderId;
    @Schema(description = "维修费，分")
    private Integer repairFee;
    @Schema(description = "寄回运费，分")
    private Integer returnFreight;
    @Schema(description = "应付合计，分")
    private Integer payableAmount;
    @Schema(description = "费用说明")
    private String feeRemark;
    @Schema(description = "支付状态")
    private Integer payStatus;
    @Schema(description = "支付状态名")
    private String payStatusName;
    @Schema(description = "退款状态：0 未退款 10 退款中 20 已退款 30 退款失败")
    private Integer refundStatus;
    @Schema(description = "退款状态名")
    private String refundStatusName;
    @Schema(description = "完成维修时间；已完成为非空")
    private java.time.LocalDateTime completeTime;
    @Schema(description = "维修订单支付状态（契约字段名；与 payStatus 同值）", example = "0")
    private Integer repairOrderPayStatus;
    @Schema(description = "维修订单（契约形状；同时保留上面的扁平字段）")
    private AppCustomerServiceRepairOrderRespVO repairOrder;
    @Schema(description = "回收邮寄地址；未配置为 null")
    private AppCustomerServiceAddressRespVO returnAddress;
    @Schema(description = "客户可见的流转记录")
    private List<AppCustomerServiceLogRespVO> logs;

}
