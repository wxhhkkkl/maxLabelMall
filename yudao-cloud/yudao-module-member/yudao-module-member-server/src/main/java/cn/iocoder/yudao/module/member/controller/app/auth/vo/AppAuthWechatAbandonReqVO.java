package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "用户 APP - 微信扫码登录：放弃本次登录 Request VO")
@Data
public class AppAuthWechatAbandonReqVO {

    @Schema(description = "票据号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "票据不能为空")
    private String ticket;

}
