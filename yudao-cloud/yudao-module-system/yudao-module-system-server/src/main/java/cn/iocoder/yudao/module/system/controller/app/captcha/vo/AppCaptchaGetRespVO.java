package cn.iocoder.yudao.module.system.controller.app.captcha.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 获得图形验证码的响应。
 *
 * ⚠️ 这是把 aj-captcha 的 `ResponseModel.repData` **重新包成项目自己的 VO** ——
 * 直接透传 `{repCode, repData}` 的话，会被 C 端 axios 响应拦截器按
 * `body.code !== 0` 判成失败（它只认 `{code,data,msg}` 这套信封）。
 */
@Schema(description = "用户 App - 获得图形验证码 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class AppCaptchaGetRespVO {

    @Schema(description = "底图（带缺口），base64")
    private String originalImageBase64;

    @Schema(description = "拼图块，base64")
    private String jigsawImageBase64;

    @Schema(description = "本次验证码的 token，校验时要原样带回")
    private String token;

    @Schema(description = "本次验证码的 AES 密钥（前端用它加密 pointJson 与 captchaVerification）")
    private String secretKey;

}
