package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 交易订单创建 Request VO")
@Data
public class AppTradeOrderCreateReqVO extends AppTradeOrderSettlementReqVO {

    /**
     * 买家留言（下单时客户自己填的，与后台的「商家备注」`remark` 是两个字段）
     *
     * 落库到 `trade_order.user_remark`，它是 **varchar(200)**。
     *
     * ⚠️ 长度必须在这里显式卡：MySQL 非严格模式下会**静默截断**超长值 ——
     * 客户写的字被砍掉一截，而系统里没有任何痕迹（与发票那边 `InvoiceValidator`
     * 卡列宽同一理由）。
     */
    @Schema(description = "买家留言", example = "请在工作日送")
    @Size(max = 200, message = "买家留言最多 200 个字符")
    private String remark;

    /**
     * 开票信息（006）
     *
     * ⚠️ **不传 = 不开票**，此时不产生任何开票记录（FR-159）—— 前端必须用 `undefined`
     * 而不是 `null` / 空对象，后者语义含混（"申请了但字段全空"）。
     *
     * ⚠️ 它与订单**在同一事务**里落库（Q32）：要么都成、要么都不成。
     * 校验失败（缺项 / 税号非法）会**拒绝整单** —— 那是客户能改的输入错误；
     * 而"实付 0 元"是**忽略**（订单照下），见 `TradeOrderInvoiceServiceImpl#createInvoice`。
     */
    private AppInvoiceApplyBaseVO invoice;

    /**
     * 支付渠道（008 对公转账）
     *
     * ⚠️ **下单时只接受 {@code offline}**，其余渠道（`wx_lite` / `alipay_pc` …）是**支付时才选**的 ——
     * 下单这一刻客户只有"要不要走线下打款"这一个决定。传了别的值会被拒，
     * 免得出现"单还没下就已经指定了微信"这种半截状态。
     *
     * **不传（或传空）= 在线支付**，行为与改动前**逐字一致**（FR-211）。
     *
     * 传了 `offline` 之后：订单进入「**待收款**」（不建在线支付单、前台不给在线支付入口），
     * 等财务在后台「确认收款」才进入待发货。详见 `specs/008-corporate-transfer/`。
     */
    @Schema(description = "支付渠道：仅支持传 offline（对公转账）；不传 = 在线支付", example = "offline")
    private String payChannelCode;

    @AssertTrue(message = "配送方式不能为空")
    @JsonIgnore
    public boolean isDeliveryTypeNotNull() {
        return getDeliveryType() != null;
    }

}
