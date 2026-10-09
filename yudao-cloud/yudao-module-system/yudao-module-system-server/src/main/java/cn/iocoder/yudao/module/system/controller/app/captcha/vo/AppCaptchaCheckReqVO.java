package cn.iocoder.yudao.module.system.controller.app.captcha.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@Schema(description = "用户 App - 校验图形验证码 Request VO")
@Data
public class AppCaptchaCheckReqVO {

    @Schema(description = "验证码类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "blockPuzzle")
    @NotEmpty(message = "验证码类型不能为空")
    private String captchaType;

    @Schema(description = "本次验证码的 token", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "token 不能为空")
    private String token;

    @Schema(description = "用户作答。滑块场景是 `{\"x\":123.5,\"y\":5.0}` 经 secretKey **AES 加密后**的字符串",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "作答不能为空")
    private String pointJson;

}
