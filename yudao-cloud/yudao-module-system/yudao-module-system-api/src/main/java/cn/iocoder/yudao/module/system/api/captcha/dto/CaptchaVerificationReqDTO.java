package cn.iocoder.yudao.module.system.api.captcha.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 图形验证码校验 Request DTO")
@Data
public class CaptchaVerificationReqDTO {

    @Schema(description = "图形验证码的校验凭据（前端滑块通过后拿到的 captchaVerification）",
            example = "Xl5mX3nQ...")
    private String captchaVerification;

}
