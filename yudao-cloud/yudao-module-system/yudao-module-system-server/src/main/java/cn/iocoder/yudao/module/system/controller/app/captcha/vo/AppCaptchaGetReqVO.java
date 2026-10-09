package cn.iocoder.yudao.module.system.controller.app.captcha.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 获得图形验证码 Request VO")
@Data
public class AppCaptchaGetReqVO {

    @Schema(description = "验证码类型。默认按全局配置 aj.captcha.type（本项目为 blockPuzzle 滑块拼图）",
            example = "blockPuzzle")
    private String captchaType;

}
