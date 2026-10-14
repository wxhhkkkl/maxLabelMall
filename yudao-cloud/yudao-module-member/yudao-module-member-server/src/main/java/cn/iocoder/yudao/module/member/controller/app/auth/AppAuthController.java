package cn.iocoder.yudao.module.member.controller.app.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;
import cn.iocoder.yudao.module.member.convert.auth.AuthConvert;
import cn.iocoder.yudao.module.member.service.auth.MemberAuthService;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxJsapiSignatureRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 APP - 认证")
@RestController
@RequestMapping("/member/auth")
@Validated
@Slf4j
public class AppAuthController {

    @Resource
    private MemberAuthService authService;

    @Resource
    private SocialClientApi socialClientApi;

    @Resource
    private SecurityProperties securityProperties;

    @PostMapping("/login")
    @Operation(summary = "使用手机 + 密码登录")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> login(@RequestBody @Valid AppAuthLoginReqVO reqVO) {
        return success(authService.login(reqVO));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出系统")
    @PermitAll
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isNotBlank(token)) {
            authService.logout(token);
        }
        return success(true);
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "刷新令牌")
    @Parameter(name = "refreshToken", description = "刷新令牌", required = true)
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        return success(authService.refreshToken(refreshToken));
    }

    // ========== 短信登录相关 ==========

    @PostMapping("/sms-login")
    @Operation(summary = "使用手机 + 验证码登录")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> smsLogin(@RequestBody @Valid AppAuthSmsLoginReqVO reqVO) {
        return success(authService.smsLogin(reqVO));
    }

    @PostMapping("/send-sms-code")
    @Operation(summary = "发送手机验证码")
    @PermitAll
    public CommonResult<Boolean> sendSmsCode(@RequestBody @Valid AppAuthSmsSendReqVO reqVO) {
        authService.sendSmsCode(getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/validate-sms-code")
    @Operation(summary = "校验手机验证码")
    @PermitAll
    public CommonResult<Boolean> validateSmsCode(@RequestBody @Valid AppAuthSmsValidateReqVO reqVO) {
        authService.validateSmsCode(getLoginUserId(), reqVO);
        return success(true);
    }

    // ========== 社交登录相关 ==========

    @GetMapping("/social-auth-redirect")
    @Operation(summary = "社交授权的跳转")
    @Parameters({
            @Parameter(name = "type", description = "社交类型", required = true),
            @Parameter(name = "redirectUri", description = "回调路径")
    })
    @PermitAll
    public CommonResult<String> socialAuthRedirect(@RequestParam("type") Integer type,
                                                   @RequestParam("redirectUri") String redirectUri) {
        return CommonResult.success(authService.getSocialAuthorizeUrl(type, redirectUri));
    }

    @PostMapping("/social-login")
    @Operation(summary = "社交快捷登录，使用 code 授权码", description = "适合未登录的用户，但是社交账号已绑定用户")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> socialLogin(@RequestBody @Valid AppAuthSocialLoginReqVO reqVO) {
        return success(authService.socialLogin(reqVO));
    }

    @PostMapping("/weixin-mini-app-login")
    @Operation(summary = "微信小程序的一键登录")
    @PermitAll
    public CommonResult<AppAuthLoginRespVO> weixinMiniAppLogin(@RequestBody @Valid AppAuthWeixinMiniAppLoginReqVO reqVO) {
        return success(authService.weixinMiniAppLogin(reqVO));
    }

    @PostMapping("/create-weixin-jsapi-signature")
    @Operation(summary = "创建微信 JS SDK 初始化所需的签名",
            description = "参考 https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/JS-SDK.html 文档")
    @PermitAll
    public CommonResult<SocialWxJsapiSignatureRespDTO> createWeixinMpJsapiSignature(@RequestParam("url") String url) {
        SocialWxJsapiSignatureRespDTO signature = socialClientApi.createWxMpJsapiSignature(
                UserTypeEnum.MEMBER.getValue(), url).getCheckedData();
        return success(AuthConvert.INSTANCE.convert(signature));
    }

    // ========== 微信扫码登录（specs/005-wechat-scan-login） ==========

    @PostMapping("/wechat/scan/create")
    @Operation(summary = "微信扫码登录：签发登录票据",
            description = "电脑端点「微信登录」时调。返回票据号与二维码内容（微信授权页地址，已带上本次的 state）")
    @PermitAll
    public CommonResult<AppAuthWechatScanCreateRespVO> createWechatScanTicket(
            @RequestBody @Valid AppAuthWechatScanCreateReqVO reqVO) {
        return success(authService.createWechatScanTicket(reqVO.getRedirectUri()));
    }

    @PostMapping("/wechat/callback")
    @Operation(summary = "微信扫码登录：手机侧授权完成的回调",
            description = "客户在手机微信里点完「允许」后，回跳页面（跑在手机上）把 code + state 发到这里。"
                    + "**这一步只识别身份，不建会员、不发令牌**")
    @PermitAll
    public CommonResult<Boolean> wechatScanCallback(@RequestBody @Valid AppAuthWechatCallbackReqVO reqVO) {
        authService.handleWechatCallback(reqVO);
        return success(true);
    }

    @PostMapping("/wechat/complete")
    @Operation(summary = "微信扫码登录：提交补全页",
            description = "**这一步才建号（或绑到既有会员）、才发令牌**。令牌不从这里返回 —— "
                    + "电脑端继续轮询 scan/query 取")
    @PermitAll
    public CommonResult<Boolean> completeWechatScan(@RequestBody @Valid AppAuthWechatCompleteReqVO reqVO) {
        authService.completeWechatScan(reqVO);
        return success(true);
    }

    @PostMapping("/wechat/abandon")
    @Operation(summary = "微信扫码登录：放弃本次登录",
            description = "客户关掉补全页时调。让这次扫码**彻底作废**：不建会员、不建绑定、不留登录态。幂等")
    @PermitAll
    public CommonResult<Boolean> abandonWechatScan(@RequestBody @Valid AppAuthWechatAbandonReqVO reqVO) {
        authService.abandonWechatScanTicket(reqVO.getTicket());
        return success(true);
    }

    @GetMapping("/wechat/scan/query")
    @Operation(summary = "微信扫码登录：电脑端轮询",
            description = "状态取值 `待扫 / 待补全 / 已完成 / 已过期 / 已作废`。"
                    + "`已完成` 返回令牌的同时票据即被取走（一次性）")
    @Parameter(name = "ticket", description = "票据号", required = true)
    @PermitAll
    public CommonResult<AppAuthWechatScanQueryRespVO> queryWechatScanTicket(
            @RequestParam("ticket") String ticket) {
        return success(authService.queryWechatScanTicket(ticket));
    }

}
