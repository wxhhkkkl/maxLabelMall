package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.framework.common.validation.Mobile;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Schema(description = "用户 APP - 发送手机验证码 Request VO")
@Data
public class AppAuthSmsSendReqVO {

    @Schema(description = "手机号", example = "15601691234")
    @Mobile
    private String mobile;

    @Schema(description = "发送场景,对应 SmsSceneEnum 枚举", example = "1")
    @NotNull(message = "发送场景不能为空")
    @InEnum(SmsSceneEnum.class)
    private Integer scene;

    /**
     * 滑块验证码通过后拿到的凭据。
     *
     * ⚠️ **刻意不加 `@NotEmpty`**：验证码开关（`yudao.captcha.enable`）关闭时前端不传，
     * 这时应当照常发码。开关的判定统一在 `CaptchaApi#verification` 里做一处 ——
     * 由它返回 false 时本接口才拒绝，这样不会出现「前端弹了滑块、后端没校验」或
     * 「后端要校验、前端没传」的错配。
     */
    @Schema(description = "图形验证码凭据，验证码开启时必传", example = "Xl5mX3nQ...")
    private String captchaVerification;

}
