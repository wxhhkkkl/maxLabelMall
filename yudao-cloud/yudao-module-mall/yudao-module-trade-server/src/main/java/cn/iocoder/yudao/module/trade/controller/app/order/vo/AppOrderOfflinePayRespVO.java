package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单详情里的**对公转账**信息（008）
 *
 * ⚠️ **不是对公转账单时整个对象是 `null`**（不是空对象）—— 前端据此决定
 * "渲不渲染「待收款」区块"，与 006 的 `invoice` 同一个做法。
 *
 * ⚠️ 收款账户明细**只在这里给**，不下发到结算接口 —— 客户在还没下单时不需要看到完整账号。
 *
 * @author 芋道源码
 */
@Schema(description = "用户 App - 订单的对公转账信息")
@Data
public class AppOrderOfflinePayRespVO {

    /**
     * 是否还在**等客户打款**
     *
     * = 是对公单 **且** 订单还没付（即 spec 说的「待收款」，FR-200）。
     *
     * ⚠️ 前端 MUST 用它来**压过**"未支付 + 没有支付单 → 无需支付"那条既有判断（R47）——
     * 对公单正好命中那条判断，不压的话会把"要打款"显示成"**无需支付**"。
     */
    @Schema(description = "是否待收款（等客户打款）", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean waiting;

    @Schema(description = "收款户名", example = "[[收款户名占位]]")
    private String accountName;

    @Schema(description = "收款账号", example = "[[收款账号占位]]")
    private String accountNo;

    @Schema(description = "开户行", example = "[[开户行占位]]")
    private String bankName;

    @Schema(description = "给客户的提示（如「请在转账附言里写订单号」）")
    private String tip;

}
