package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "用户 APP - 微信扫码登录：签发票据 Request VO")
@Data
public class AppAuthWechatScanCreateReqVO {

    @Schema(description = "授权后回跳的前端页面地址（完整 URL，必须落在公众号后台配的「网页授权域名」下）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "https://new.yuwangchenfa.com/wechat/login")
    @NotEmpty(message = "回跳地址不能为空")
    private String redirectUri;

}
