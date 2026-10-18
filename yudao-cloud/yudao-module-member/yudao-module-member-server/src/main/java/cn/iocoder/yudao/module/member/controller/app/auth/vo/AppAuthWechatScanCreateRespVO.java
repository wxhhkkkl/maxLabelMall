package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 APP - 微信扫码登录：签发票据 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppAuthWechatScanCreateRespVO {

    @Schema(description = "票据号。电脑端拿它轮询；**不可猜测，在待补全期间等价于登录凭据**",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "8f3a0b...")
    private String ticket;

    @Schema(description = "二维码的内容 —— 微信授权页地址（已带上本次扫码的 state）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String qrContent;

    @Schema(description = "票据的存活秒数", requiredMode = Schema.RequiredMode.REQUIRED, example = "300")
    private Integer expiresIn;

}
