package cn.iocoder.yudao.module.trade.controller.app.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单详情里的开票信息 Response VO（`AppTradeOrderDetailRespVO#invoice`）
 *
 * ⚠️ **没有申请时整个 `invoice` 是 `null`**（不是空对象）—— 前端据此决定
 * "整块不渲染"还是"给申请开票入口"（FR-176 的四态）。
 *
 * @author 芋道源码
 */
@Data
public class AppOrderInvoiceRespVO {

    @Schema(description = "状态：0 待开票，10 已开票，20 已作废", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "发票类型：1 电子普通发票，2 电子增值税专用发票", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer type;

    @Schema(description = "发票类型名（由后端枚举给出，前端不自行命名）", example = "电子增值税专用发票")
    private String typeName;

    @Schema(description = "抬头类型：1 企业，2 个人", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer titleKind;

    @Schema(description = "抬头类型名（由后端枚举给出，前端不自行命名）", example = "企业")
    private String titleKindName;

    @Schema(description = "抬头名称", example = "南京路桥机械有限公司")
    private String titleName;

    @Schema(description = "纳税人识别号", example = "91320104660690975E")
    private String taxNo;

    @Schema(description = "注册地址")
    private String titleAddress;

    @Schema(description = "注册电话")
    private String titlePhone;

    @Schema(description = "开户行及账号")
    private String bankName;

    @Schema(description = "开票金额，单位：分（= 申请时的订单实付快照）", example = "7800")
    private Integer amount;

    @Schema(description = "发票文件地址（已开票才有；链接公网可读，见 spec 的 Assumptions）")
    private String fileUrl;

    @Schema(description = "开票时间")
    private LocalDateTime issueTime;

    @Schema(description = "申请时间")
    private LocalDateTime createTime;

}
