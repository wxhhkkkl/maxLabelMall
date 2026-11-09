package cn.iocoder.yudao.module.pay.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Schema(description = "RPC 服务 - 支付订单提交 Request DTO")
@Data
public class PayOrderSubmitReqDTO {

    @Schema(description = "支付单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "支付单编号不能为空")
    private Long id;

    @Schema(description = "支付渠道", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx_native")
    @NotEmpty(message = "支付渠道不能为空")
    private String channelCode;

    @Schema(description = "支付渠道的额外参数，例如说微信公众号需要传递 openid")
    private Map<String, String> channelExtras;

    @Schema(description = "展示模式", example = "qr_code")
    private String displayMode;

    @Schema(description = "回跳地址")
    private String returnUrl;

}
