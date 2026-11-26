package cn.iocoder.yudao.module.trade.controller.admin.invoice.vo;

import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 代客申请发票 Request VO（023）
 *
 * 字段基类与 C 端结算/补开共用（`AppInvoiceApplyBaseVO`）—— 校验唯一来源是
 * `InvoiceValidator`，**不要**另建一份字段 VO。
 */
@Schema(description = "管理后台 - 代客申请发票 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminTradeOrderInvoiceCreateReqVO extends AppInvoiceApplyBaseVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "订单编号不能为空")
    private Long orderId;

}
