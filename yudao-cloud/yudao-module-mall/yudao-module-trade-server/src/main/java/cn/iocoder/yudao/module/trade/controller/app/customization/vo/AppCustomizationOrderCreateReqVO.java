package cn.iocoder.yudao.module.trade.controller.app.customization.vo;

import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 用户 App - 创建定制订单
 */
@Schema(description = "用户 App - 创建定制订单")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppCustomizationOrderCreateReqVO extends AppCustomizationSpecReqVO {

    @Schema(description = "客户看到的应付金额（含运费），分", requiredMode = Schema.RequiredMode.REQUIRED, example = "34900")
    @NotNull(message = "应付金额不能为空")
    private Integer expectedAmount;

    @Schema(description = "收件地址编号", example = "7")
    private Long addressId;

    @Schema(description = "配送方式：1 快递", example = "1")
    private Integer deliveryType;

    @Schema(description = "开票信息；不传 = 不开票")
    @Valid
    private AppInvoiceApplyBaseVO invoice;

}
