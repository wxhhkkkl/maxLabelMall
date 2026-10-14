package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "用户 APP - 微信扫码登录：手机侧授权完成的回调 Request VO")
@Data
public class AppAuthWechatCallbackReqVO {

    @Schema(description = "微信授权码（一次性、短命）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "授权码不能为空")
    private String code;

    @Schema(description = "本次扫码的 state —— 由我们签发，用来找回对应的票据",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "state 不能为空")
    private String state;

}
