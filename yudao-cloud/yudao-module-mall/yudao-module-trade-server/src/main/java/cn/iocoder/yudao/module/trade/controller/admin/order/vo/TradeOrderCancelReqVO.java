package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - **取消订单** Request VO（008 / FR-213）
 *
 * ⚠️ 为什么要新增这个能力：对公转账单**不参与超时自动取消**（FR-212，客户打款要几天），
 * 所以客户弃单后**必须有人能清掉它** —— 而后台此前**根本没有取消订单的功能**。
 * 一经做出就是**通用的**（所有未支付订单都能取消），没必要只给对公单开一个口子。
 *
 * @author 芋道源码
 */
@Schema(description = "管理后台 - 取消订单 Request VO")
@Data
public class TradeOrderCancelReqVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "900")
    @NotNull(message = "订单编号不能为空")
    private Long id;

    /**
     * 取消原因（选填，但**建议填**）—— 写进订单备注，供后来的人看懂"这单为什么没了"
     */
    @Schema(description = "取消原因", example = "客户已放弃打款")
    private String reason;

}
