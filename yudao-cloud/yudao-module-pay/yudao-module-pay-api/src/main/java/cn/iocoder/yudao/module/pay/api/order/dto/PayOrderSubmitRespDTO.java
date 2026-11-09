package cn.iocoder.yudao.module.pay.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 支付订单提交 Response DTO")
@Data
public class PayOrderSubmitRespDTO {

    @Schema(description = "支付状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "展示模式", requiredMode = Schema.RequiredMode.REQUIRED, example = "qr_code")
    private String displayMode;

    @Schema(description = "展示内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayContent;

}
