package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import cn.iocoder.yudao.module.trade.controller.app.order.vo.item.AppTradeOrderItemRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户 App - 订单交易的分页项 Response VO")
@Data
public class AppTradeOrderPageItemRespVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "订单流水号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1146347329394184195")
    private String no;

    @Schema(description = "订单类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer type;

    @Schema(description = "订单状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "购买的商品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer productCount;

    @Schema(description = "是否评价", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean commentStatus;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    // ========== 价格 + 支付基本信息 ==========

    @Schema(description = "支付订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long payOrderId;

    @Schema(description = "应付金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    private Integer payPrice;

    // ========== 收件 + 物流基本信息 ==========

    @Schema(description = "配送方式", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer deliveryType;

    /**
     * 发货物流单号；未发货 / 虚拟商品 / 自提为 null
     *
     * C 端「我的订单」只在它非空时显示「查看物流」入口（010 / FR-273）。
     * ⚠️ 不要用订单状态代替：历史导入单里存在「已完成但没有运单号」的订单。
     */
    @Schema(description = "发货物流单号", example = "SF1234567890")
    private String logisticsNo;

    /**
     * 订单项数组
     */
    private List<AppTradeOrderItemRespVO> items;

    // ========== 营销基本信息 ==========

    @Schema(description = "拼团记录编号", example = "100")
    private Long combinationRecordId;

    /**
     * 开票状态（006 / FR-178）
     *
     * ⚠️ **没申请开票时是 `null`** —— 列表上什么都不显示。
     * 取值同订单详情：0 待开票 / 10 已开票 / 20 已作废。
     */
    @Schema(description = "开票状态：0 待开票，10 已开票，20 已作废；未申请为 null", example = "0")
    private Integer invoiceStatus;

    /**
     * 票种（011）：1 电子普通发票 / 2 电子增值税专用发票 / 3 收据
     *
     * ⚠️ 与 {@link #invoiceStatus} 同源、同时为 `null` —— 列表上「待开票」下面要带出票种。
     */
    @Schema(description = "票种：1 电子普通发票，2 电子增值税专用发票，3 收据；未申请为 null", example = "1")
    private Integer invoiceType;

    /**
     * 是不是**还没确认收款的对公转账单**（008 / FR-200）
     *
     * = 是对公单 **且** 订单还没付、也没确认收款。
     *
     * ⚠️ 客户侧靠它把列表上的状态显示成「**待收款**」而不是「待支付」，
     * **并去掉「立即支付」入口** —— 对公单在客户侧没有任何在线支付入口（FR-201），
     * 留个「立即支付」只会把人送到一个没有支付按钮的详情页。
     * 底层状态仍是 {@link cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum#UNPAID}，
     * 所以列表的**筛选**照旧按「待支付」筛。
     */
    @Schema(description = "是否待收款（对公转账单且未确认收款）", example = "false")
    private Boolean offlineWaiting;

}
