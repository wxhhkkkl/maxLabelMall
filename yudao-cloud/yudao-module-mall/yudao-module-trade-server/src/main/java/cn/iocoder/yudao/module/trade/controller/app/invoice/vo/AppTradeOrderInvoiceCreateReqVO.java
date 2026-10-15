package cn.iocoder.yudao.module.trade.controller.app.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单**补开**发票 Request VO（US20）
 *
 * 下单时申请走的是 `AppTradeOrderCreateReqVO#invoice`（同一个父类）；这里是订单已经存在之后的自助补开，
 * 所以多一个 `orderId`。**字段与校验完全共用父类那套** —— 补开不该有一条更宽松的旁路。
 *
 * @author 芋道源码
 */
@Schema(description = "用户 App - 订单补开票 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppTradeOrderInvoiceCreateReqVO extends AppInvoiceApplyBaseVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "订单编号不能为空")
    private Long orderId;

}
