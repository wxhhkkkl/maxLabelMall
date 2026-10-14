package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户 APP - 微信扫码登录：**提交补全** Request VO
 *
 * ⚠️ 这里**没有头像字段** —— 交付契约 §4 里那个 `avatarUrl` 是可省的：
 * 补全页出现时客户**还没登录**，而头像上传接口要求登录，所以自选头像必须**提交成功之后**再传
 * （FR-151）。补全页默认展示的微信头像，在建号那一刻就已经写进会员了。
 */
@Schema(description = "用户 APP - 微信扫码登录：提交补全 Request VO")
@Data
public class AppAuthWechatCompleteReqVO {

    @Schema(description = "票据号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "票据不能为空")
    private String ticket;

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @Mobile
    private String mobile;

    @Schema(description = "短信验证码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1234")
    @NotEmpty(message = "验证码不能为空")
    private String smsCode;

    @Schema(description = "公司名称（选填）", example = "某某印刷厂")
    @Size(max = 64, message = "公司名称长度不能超过 64 个字符")
    private String company;

}
