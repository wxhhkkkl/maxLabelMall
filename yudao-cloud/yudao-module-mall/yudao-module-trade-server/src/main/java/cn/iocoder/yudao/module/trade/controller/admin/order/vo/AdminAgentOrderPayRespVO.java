package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "管理后台 - 代客订单发起支付 Response VO")
@Data
@Accessors(chain = true)
public class AdminAgentOrderPayRespVO {

    @Schema(description = "支付状态：0 待支付 / 10 已支付", example = "0")
    private Integer status;

    @Schema(description = "展示模式：qr_code / url", example = "qr_code")
    private String displayMode;

    @Schema(description = "展示内容：二维码内容或支付链接")
    private String displayContent;

}
