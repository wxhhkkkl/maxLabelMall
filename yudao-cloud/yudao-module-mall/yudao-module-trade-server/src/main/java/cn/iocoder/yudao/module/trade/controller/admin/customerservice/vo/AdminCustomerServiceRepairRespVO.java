package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 维修申请 Response VO")
@Data
public class AdminCustomerServiceRepairRespVO {

    @Schema(description = "维修申请编号", example = "601")
    private Long id;
    @Schema(description = "会员编号", example = "88")
    private Long memberId;
    @Schema(description = "会员昵称")
    private String memberNickname;
    @Schema(description = "会员手机号")
    private String memberMobile;
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
    @Schema(description = "来源：1 客户自助 2 客服代建")
    private Integer source;
    @Schema(description = "来源名")
    private String sourceName;
    @Schema(description = "开单人编号（来源为会员或管理员）")
    private Long creatorId;
    @Schema(description = "开单人昵称")
    private String creatorName;
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
    @Schema(description = "退款状态")
    private Integer refundStatus;
    @Schema(description = "退款状态名")
    private String refundStatusName;
    @Schema(description = "回收邮寄地址快照；未配置为 null")
    private AdminCustomerServiceAddressReqVO returnAddress;
    @Schema(description = "流转记录")
    private List<AdminCustomerServiceLogRespVO> logs;

}
