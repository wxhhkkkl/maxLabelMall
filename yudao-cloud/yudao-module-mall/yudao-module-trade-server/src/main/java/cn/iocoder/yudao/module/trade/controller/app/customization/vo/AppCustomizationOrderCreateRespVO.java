package cn.iocoder.yudao.module.trade.controller.app.customization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户 App - 创建定制订单响应
 *
 * `changed = true` 时表示价格已变（Q101）：未建单，前端展示新报价并让客户确认。
 */
@Schema(description = "用户 App - 创建定制订单响应")
@Data
public class AppCustomizationOrderCreateRespVO {

    @Schema(description = "价格是否已变", example = "false")
    private Boolean changed;

    @Schema(description = "价格已变时的新报价")
    private AppCustomizationQuoteRespVO quote;

    @Schema(description = "交易订单编号", example = "123")
    private Long id;
    @Schema(description = "支付订单编号", example = "456")
    private Long payOrderId;
    @Schema(description = "定制订单扩展编号", example = "789")
    private Long customizationOrderId;

}
