package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;

import jakarta.validation.Valid;

/**
 * 会员的认证 Service 接口
 *
 * 提供用户的账号密码登录、token 的校验等认证相关的功能
 *
 * @author 芋道源码
 */
public interface MemberAuthService {

    /**
     * 手机 + 密码登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO login(@Valid AppAuthLoginReqVO reqVO);

    /**
     * 基于 token 退出登录
     *
     * @param token token
     */
    void logout(String token);

    /**
     * 手机 + 验证码登陆
     *
     * @param reqVO    登陆信息
     * @return 登录结果
     */
    AppAuthLoginRespVO smsLogin(@Valid AppAuthSmsLoginReqVO reqVO);

    /**
     * 社交登录，使用 code 授权码
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO socialLogin(@Valid AppAuthSocialLoginReqVO reqVO);

    // ========== 微信扫码登录（specs/005-wechat-scan-login） ==========

    /**
     * 微信扫码登录：**签发一张登录票据**（电脑端点「微信登录」时调）
     *
     * @param redirectUri 授权后回跳的前端页面地址（完整 URL，须落在公众号的网页授权域名下）
     * @return 票据号 + 二维码内容 + 存活秒数
     */
    AppAuthWechatScanCreateRespVO createWechatScanTicket(String redirectUri);

    /**
     * 微信扫码登录：**手机侧授权完成的回调**
     *
     * ⚠️ 只做"识别身份"：把 openid 写进票据。
     * **MUST NOT 建会员、MUST NOT 建绑定、MUST NOT 签发令牌**（FR-132 / FR-150）——
     * 建号要等客户在补全页提交成功（Q26）。
     *
     * 例外：该 openid **已经绑过会员**、且那个会员有手机号时，直接完成登录（FR-133）
     */
    void handleWechatCallback(@Valid AppAuthWechatCallbackReqVO reqVO);

    /**
     * 微信扫码登录：**电脑端轮询**
     *
     * @param ticket 票据号
     * @return 状态与（视状态而定的）载荷；票据不存在时返回「已过期」
     */
    AppAuthWechatScanQueryRespVO queryWechatScanTicket(String ticket);

    /**
     * 微信扫码登录：**提交补全页** —— 这一步才建号（或绑到既有会员）并发令牌（Q26 / FR-134 / FR-150）
     *
     * 客户在电脑上填完手机号（+ 选填公司名称）后调。令牌不从这里返回 ——
     * 电脑端继续轮询 {@link #queryWechatScanTicket(String)} 取。
     *
     * @param reqVO 票据、手机号、短信验证码、公司名称
     */
    void completeWechatScan(@Valid AppAuthWechatCompleteReqVO reqVO);

    /**
     * 微信扫码登录：**客户关掉了补全页** —— 让这次扫码彻底作废（FR-148）
     *
     * 幂等：票据本来就过期了也算"已经作废"，不报错。
     */
    void abandonWechatScanTicket(String ticket);

    /**
     * 微信小程序的一键登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AppAuthLoginRespVO weixinMiniAppLogin(AppAuthWeixinMiniAppLoginReqVO reqVO);

    /**
     * 获得社交认证 URL
     *
     * @param type 社交平台类型
     * @param redirectUri 跳转地址
     * @return 认证 URL
     */
    String getSocialAuthorizeUrl(Integer type, String redirectUri);

    /**
     * 给用户发送短信验证码
     *
     * @param userId 用户编号
     * @param reqVO 发送信息
     */
    void sendSmsCode(Long userId, AppAuthSmsSendReqVO reqVO);

    /**
     * 校验短信验证码是否正确
     *
     * @param userId 用户编号
     * @param reqVO 校验信息
     */
    void validateSmsCode(Long userId, AppAuthSmsValidateReqVO reqVO);

    /**
     * 刷新访问令牌
     *
     * @param refreshToken 刷新令牌
     * @return 登录结果
     */
    AppAuthLoginRespVO refreshToken(String refreshToken);

}
