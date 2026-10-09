package cn.iocoder.yudao.module.system.controller.app.captcha.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校验图形验证码的响应。
 *
 * ⚠️ 这里**不返回 `captchaVerification`** —— aj-captcha 服务端从不生成它
 * （只有 `CaptchaVO` 上的 setter，没有任何地方调用）。正确做法是前端拿本次的
 * `secretKey` 自己算：`AES(token + "---" + pointJson)`。管理端的 VerifySlide.vue
 * 就是这么做的，本项目照搬。
 */
@Schema(description = "用户 App - 校验图形验证码 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppCaptchaCheckRespVO {

    @Schema(description = "是否通过", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean success;

    @Schema(description = "不通过的原因（可直接展示给用户）", example = "验证码错误")
    private String msg;

}
