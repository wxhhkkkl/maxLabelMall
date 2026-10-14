package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户 APP - 微信扫码登录：电脑端轮询的 Response VO
 *
 * 状态取值见 `specs/005-wechat-scan-login/contracts/app-api.md` §2（**共 5 个**）：
 * `待扫 / 待补全 / 已完成 / 已过期 / 已作废`。
 *
 * ⚠️ **`待补全` 之前 MUST NOT 返回任何令牌**（FR-150）；
 * `已完成` 返回令牌的同时服务端会**把票据删掉**（一次性，FR-149）。
 */
@Schema(description = "用户 APP - 微信扫码登录：轮询 Response VO")
@Data
public class AppAuthWechatScanQueryRespVO {

    @Schema(description = "票据状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "待补全")
    private String status;

    /**
     * 以下三项只在「待补全」时有值 —— 补全页的默认展示（微信头像与昵称）
     */
    @Schema(description = "微信昵称（补全页默认展示）", example = "张三")
    private String wxNickname;
    @Schema(description = "微信头像（补全页默认展示）")
    private String wxAvatar;

    /**
     * 以下两项**只在「已完成」时有值**
     */
    @Schema(description = "访问令牌")
    private String accessToken;
    @Schema(description = "刷新令牌")
    private String refreshToken;

}
