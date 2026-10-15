package cn.iocoder.yudao.module.trade.controller.admin.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 开票申请 Response VO
 *
 * ⚠️ `orderPayPrice` / `orderRefundPrice` 是**订单当前**的实付与已退金额，
 * 而 `amount` 是**申请那一刻**的快照 —— **部分退款不会改 `amount`**（Q33）。
 * 两个数放一起，运营开具前才看得出"这笔已经退过一部分"。
 *
 * @author 芋道源码
 */
@Data
public class TradeOrderInvoiceRespVO {

    @Schema(description = "申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "77")
    private Long id;

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long orderId;

    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "NO-20261015-0001")
    private String orderNo;

    @Schema(description = "会员编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "88")
    private Long userId;

    @Schema(description = "会员昵称（由 MemberUserApi 批量回填）", example = "张三")
    private String userNickname;

    @Schema(description = "发票类型：1 电子普通发票，2 电子增值税专用发票", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer type;

    @Schema(description = "发票类型名", example = "电子增值税专用发票")
    private String typeName;

    @Schema(description = "状态：0 待开票，10 已开票，20 已作废", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "状态名", example = "待开票")
    private String statusName;

    @Schema(description = "开票金额（申请时的订单实付快照），单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "7800")
    private Integer amount;

    @Schema(description = "订单**当前**实付金额，单位：分", example = "7800")
    private Integer orderPayPrice;

    @Schema(description = "订单**当前**已退金额，单位：分", example = "3000")
    private Integer orderRefundPrice;

    @Schema(description = "抬头名称", example = "南京路桥机械有限公司")
    private String titleName;

    @Schema(description = "纳税人识别号")
    private String taxNo;

    @Schema(description = "注册地址")
    private String titleAddress;

    @Schema(description = "注册电话")
    private String titlePhone;

    @Schema(description = "开户行及账号")
    private String bankName;

    @Schema(description = "发票文件地址（已开票才有）")
    private String invoiceFileUrl;

    @Schema(description = "开票时间")
    private LocalDateTime issueTime;

    @Schema(description = "开具人（后台用户编号）")
    private Long issueUserId;

    @Schema(description = "作废时间")
    private LocalDateTime cancelTime;

    @Schema(description = "申请时间")
    private LocalDateTime createTime;

}
