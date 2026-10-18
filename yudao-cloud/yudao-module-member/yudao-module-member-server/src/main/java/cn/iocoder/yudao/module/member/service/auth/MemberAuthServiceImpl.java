package cn.iocoder.yudao.module.member.service.auth;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.TerminalEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;
import cn.iocoder.yudao.module.member.convert.auth.AuthConvert;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.auth.bo.WechatLoginTicketBO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.captcha.CaptchaApi;
import cn.iocoder.yudao.module.system.api.captcha.dto.CaptchaVerificationReqDTO;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.module.member.controller.app.user.vo.AppMemberUserUpdateReqVO;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserBindByOpenidReqDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserBindReqDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils.getTerminal;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

/**
 * 会员的认证 Service 接口
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class MemberAuthServiceImpl implements MemberAuthService {

    @Resource
    private MemberUserService userService;
    @Resource
    private SmsCodeApi smsCodeApi;
    @Resource
    private CaptchaApi captchaApi;
    @Resource
    private LoginLogApi loginLogApi;
    @Resource
    private SocialUserApi socialUserApi;

    @Resource
    private WechatLoginTicketService wechatLoginTicketService;
    @Resource
    private SocialClientApi socialClientApi;
    @Resource
    private OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    public AppAuthLoginRespVO login(AppAuthLoginReqVO reqVO) {
        // 使用手机 + 密码，进行登录。
        MemberUserDO user = login0(reqVO.getMobile(), reqVO.getPassword());

        // 如果 socialType 非空，说明需要绑定社交用户
        String openid = null;
        if (reqVO.getSocialType() != null) {
            openid = socialUserApi.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                    reqVO.getSocialType(), reqVO.getSocialCode(), reqVO.getSocialState())).getCheckedData();
        }

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, reqVO.getMobile(), LoginLogTypeEnum.LOGIN_MOBILE, openid);
    }

    @Override
    @Transactional
    public AppAuthLoginRespVO smsLogin(AppAuthSmsLoginReqVO reqVO) {
        // 校验验证码
        String userIp = getClientIP();
        smsCodeApi.useSmsCode(AuthConvert.INSTANCE.convert(reqVO, SmsSceneEnum.MEMBER_LOGIN.getScene(), userIp)).checkError();

        // 获得获得注册用户
        MemberUserDO user = userService.createUserIfAbsent(reqVO.getMobile(), userIp, getTerminal());
        Assert.notNull(user, "获取用户失败，结果为空");

        // 校验是否禁用
        validateUserStatus(user, reqVO.getMobile(), LoginLogTypeEnum.LOGIN_SMS);

        // 如果 socialType 非空，说明需要绑定社交用户
        String openid = null;
        if (reqVO.getSocialType() != null) {
            openid = socialUserApi.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                    reqVO.getSocialType(), reqVO.getSocialCode(), reqVO.getSocialState())).getCheckedData();
        }

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, reqVO.getMobile(), LoginLogTypeEnum.LOGIN_SMS, openid);
    }

    @Override
    @Transactional
    public AppAuthLoginRespVO socialLogin(AppAuthSocialLoginReqVO reqVO) {
        // 使用 code 授权码，进行登录。然后，获得到绑定的用户编号
        SocialUserRespDTO socialUser = socialUserApi.getSocialUserByCode(UserTypeEnum.MEMBER.getValue(), reqVO.getType(),
                reqVO.getCode(), reqVO.getState()).getCheckedData();
        if (socialUser == null) {
            throw exception(AUTH_SOCIAL_USER_NOT_FOUND);
        }

        // 情况一：已绑定，直接读取用户信息
        MemberUserDO user;
        if (socialUser.getUserId() != null) {
            user = userService.getUser(socialUser.getUserId());
        // 情况二：未绑定，注册用户 + 绑定用户
        } else {
            user = userService.createUser(socialUser.getNickname(), socialUser.getAvatar(), getClientIP(), getTerminal());
            socialUserApi.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                    reqVO.getType(), reqVO.getCode(), reqVO.getState())).checkError();
        }
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }

        // 校验是否禁用
        validateUserStatus(user, user.getMobile(), LoginLogTypeEnum.LOGIN_SOCIAL);

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, user.getMobile(), LoginLogTypeEnum.LOGIN_SOCIAL, socialUser.getOpenid());
    }

    // ========== 微信扫码登录（specs/005-wechat-scan-login） ==========

    /** 从微信授权地址里抠出 `state` —— 见 createWechatScanTicket 的说明 */
    private static final Pattern AUTHORIZE_STATE = Pattern.compile("[?&]state=([^&#]+)");

    @Override
    public AppAuthWechatScanCreateRespVO createWechatScanTicket(String redirectUri) {
        // 1. 先拿授权地址。
        //    ⚠️ **justauth 在这一步会自己生成一个 state 并缓存起来**，回调时要按它比对。
        //       所以 state 只能"用它给的"，**绝不能自己另生成一个去替换** ——
        //       替换了的话，justauth 缓存的还是原来那个，回调回来的却是我们的，
        //       它会直接抛 `Illegal state [WECHAT_MP]`（2026-10-14 线上实测踩过这一脚）。
        String authorizeUri = socialClientApi.getAuthorizeUrl(SocialTypeEnum.WECHAT_MP.getType(),
                UserTypeEnum.MEMBER.getValue(), redirectUri).getCheckedData();

        // 2. 从地址里把它的 state 读出来，交给票据 —— 于是两边对得上：
        //    微信把 state 原样带回给我回调，justauth 校验通过，我们也能按 state 找回票据。
        Matcher matcher = AUTHORIZE_STATE.matcher(authorizeUri);
        if (!matcher.find()) {
            // 拿不到就说明授权地址的形状变了 —— 宁可当场炸，也别发一张注定回调失败出去的二维码
            throw new IllegalStateException("微信授权地址里没有 state：" + authorizeUri);
        }
        WechatLoginTicketBO ticket = wechatLoginTicketService.create(matcher.group(1));

        return new AppAuthWechatScanCreateRespVO(ticket.getTicket(), authorizeUri,
                (int) wechatLoginTicketService.getTtlSeconds());
    }

    @Override
    public void handleWechatCallback(AppAuthWechatCallbackReqVO reqVO) {
        // 用 code 换 openid —— 复用既有能力。**这一步只换身份，不建号**
        SocialUserRespDTO socialUser = socialUserApi.getSocialUserByCode(UserTypeEnum.MEMBER.getValue(),
                SocialTypeEnum.WECHAT_MP.getType(), reqVO.getCode(), reqVO.getState()).getCheckedData();
        if (socialUser == null) {
            throw exception(AUTH_SOCIAL_USER_NOT_FOUND);
        }

        // 例外：该 openid **已经绑过会员**、且那个会员有手机号 → 直接完成登录，不再要求补全（FR-133）
        //
        // 先看"要不要走这条捷径"，再动票据 —— 顺序反了会在票据状态不对时白白签出一个令牌
        if (socialUser.getUserId() != null) {
            MemberUserDO user = userService.getUser(socialUser.getUserId());
            if (user != null && StrUtil.isNotBlank(user.getMobile())) {
                wechatLoginTicketService.markWaitingProfile(reqVO.getState(), socialUser.getOpenid(),
                        socialUser.getNickname(), socialUser.getAvatar());
                AppAuthLoginRespVO login = createTokenAfterLoginSuccess(user, user.getMobile(),
                        LoginLogTypeEnum.LOGIN_SOCIAL, socialUser.getOpenid());
                WechatLoginTicketBO ticket = wechatLoginTicketService.getByState(reqVO.getState());
                wechatLoginTicketService.markCompleted(ticket.getTicket(), user.getId(),
                        login.getAccessToken(), login.getRefreshToken());
                return;
            }
        }

        // 其余情况：**只把微信身份写进票据**。
        //
        // ⚠️⚠️ 这里 MUST NOT 建会员、MUST NOT 建绑定、MUST NOT 签发令牌（FR-132 / FR-150 / Q26）——
        //       那些要等客户在补全页提交成功才做。这一行就是"延后建号"的全部秘密。
        wechatLoginTicketService.markWaitingProfile(reqVO.getState(), socialUser.getOpenid(),
                socialUser.getNickname(), socialUser.getAvatar());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeWechatScan(AppAuthWechatCompleteReqVO reqVO) {
        // 1. 票据必须存在、且处于「待补全」
        WechatLoginTicketBO ticket = wechatLoginTicketService.get(reqVO.getTicket());
        if (ticket == null) {
            throw exception(WECHAT_LOGIN_TICKET_NOT_EXISTS);
        }
        if (!WechatLoginTicketStatusEnum.WAITING_PROFILE.getStatus().equals(ticket.getStatus())) {
            throw exception(WECHAT_LOGIN_TICKET_STATUS_INVALID);
        }

        // 2. 手机号**一律**先过短信验证码（FR-147 / Q22=A）—— 手打的号不等于客户拥有它。
        //    复用**登录场景**（scene=1）：它的短信模板是现成的，不必让运营再去申请一个新场景的模板；
        //    语义上也说得通 —— 这一步就是用短信证明"这个号是我的"。
        String userIp = getClientIP();
        smsCodeApi.useSmsCode(new SmsCodeUseReqDTO().setMobile(reqVO.getMobile())
                .setCode(reqVO.getSmsCode()).setScene(SmsSceneEnum.MEMBER_LOGIN.getScene())
                .setUsedIp(userIp)).checkError();

        // 3. 手机号命中既有会员就复用，否则**此刻建号**（Q26：「延后建号」里那个"建"就是这里）
        MemberUserDO user = userService.getUserByMobile(reqVO.getMobile());
        if (user == null) {
            user = userService.createUserIfAbsent(reqVO.getMobile(), userIp, getTerminal());
            Assert.notNull(user, "获取用户失败，结果为空");
            // 新号：把微信带来的昵称/头像与客户填的公司名字一并落上
            userService.updateUser(user.getId(),
                    buildProfileUpdate(ticket.getWxNickname(), ticket.getWxAvatar(), reqVO.getCompany()));
        } else if (StrUtil.isNotBlank(reqVO.getCompany())) {
            // 老客户：**只补公司名称** —— 绝不覆盖人家自己设过的昵称与头像
            userService.updateUser(user.getId(), buildProfileUpdate(null, null, reqVO.getCompany()));
        }

        // 4. 校验账号状态（被禁用的挡在门外）
        validateUserStatus(user, reqVO.getMobile(), LoginLogTypeEnum.LOGIN_SOCIAL);

        // 5. 绑定微信身份。
        //    ⚠️ 走的是**按 openid 绑定**：它会**拒绝**"该会员已绑过别的微信"（Q21=C / FR-146），
        //       而不像既有的 bindSocialUser 那样把先绑的那个静默顶掉。
        //       绑定失败会在这里抛出去 —— 于是**后面一个字节都不会执行**（不发令牌、不标完成）。
        socialUserApi.bindSocialUserByOpenid(new SocialUserBindByOpenidReqDTO(user.getId(),
                UserTypeEnum.MEMBER.getValue(), SocialTypeEnum.WECHAT_MP.getType(),
                ticket.getOpenid())).checkError();

        // 6. 到这一步才签发令牌，并把票据标成「已完成」—— 电脑端轮询就能取走它
        AppAuthLoginRespVO login = createTokenAfterLoginSuccess(user, reqVO.getMobile(),
                LoginLogTypeEnum.LOGIN_SOCIAL, ticket.getOpenid());
        wechatLoginTicketService.markCompleted(ticket.getTicket(), user.getId(),
                login.getAccessToken(), login.getRefreshToken());
    }

    /** 组装一个"只改想改的那几项"的资料更新（`updateUser` 走 `updateById`，null 项会被忽略） */
    private static AppMemberUserUpdateReqVO buildProfileUpdate(String nickname, String avatar, String company) {
        AppMemberUserUpdateReqVO updateVO = new AppMemberUserUpdateReqVO();
        updateVO.setNickname(nickname);
        updateVO.setAvatar(avatar);
        updateVO.setCompany(company);
        return updateVO;
    }

    @Override
    public void abandonWechatScanTicket(String ticket) {
        // 幂等：票据本来就过期了也算"已经作废"（FR-148）—— 客户关页面关得晚一点不该看到错误
        wechatLoginTicketService.abandon(ticket);
    }

    @Override
    public AppAuthWechatScanQueryRespVO queryWechatScanTicket(String ticket) {
        WechatLoginTicketBO ticketBO = wechatLoginTicketService.get(ticket);
        AppAuthWechatScanQueryRespVO resp = new AppAuthWechatScanQueryRespVO();
        // 不存在 = 已过期（TTL 到了，或已被取走）—— 刻意不区分这两者，见 data-model.md §4
        if (ticketBO == null) {
            resp.setStatus(WechatLoginTicketStatusEnum.EXPIRED.getDescription());
            return resp;
        }
        WechatLoginTicketStatusEnum status = WechatLoginTicketStatusEnum.of(ticketBO.getStatus());
        resp.setStatus(status != null ? status.getDescription()
                : WechatLoginTicketStatusEnum.EXPIRED.getDescription());
        // 「待补全」→ 补全页的默认展示（微信昵称与头像）
        if (WechatLoginTicketStatusEnum.WAITING_PROFILE == status) {
            resp.setWxNickname(ticketBO.getWxNickname());
            resp.setWxAvatar(ticketBO.getWxAvatar());
        }
        // 「已完成」→ 交出令牌，**并把票据删掉**（一次性，FR-149）
        if (WechatLoginTicketStatusEnum.COMPLETED == status) {
            resp.setAccessToken(ticketBO.getAccessToken());
            resp.setRefreshToken(ticketBO.getRefreshToken());
            wechatLoginTicketService.remove(ticket);
        }
        return resp;
    }

    @Override
    public AppAuthLoginRespVO weixinMiniAppLogin(AppAuthWeixinMiniAppLoginReqVO reqVO) {
        // 获得对应的手机号信息
        SocialWxPhoneNumberInfoRespDTO phoneNumberInfo = socialClientApi.getWxMaPhoneNumberInfo(
                UserTypeEnum.MEMBER.getValue(), reqVO.getPhoneCode()).getCheckedData();
        Assert.notNull(phoneNumberInfo, "获得手机信息失败，结果为空");

        // 获得获得注册用户
        MemberUserDO user = userService.createUserIfAbsent(phoneNumberInfo.getPurePhoneNumber(),
                getClientIP(), TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal());
        Assert.notNull(user, "获取用户失败，结果为空");
        validateUserStatus(user, user.getMobile(), LoginLogTypeEnum.LOGIN_SOCIAL);

        // 绑定社交用户
        String openid = socialUserApi.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                SocialTypeEnum.WECHAT_MINI_PROGRAM.getType(), reqVO.getLoginCode(), reqVO.getState())).getCheckedData();

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, user.getMobile(), LoginLogTypeEnum.LOGIN_SOCIAL, openid);
    }

    private AppAuthLoginRespVO createTokenAfterLoginSuccess(MemberUserDO user, String mobile,
                                                            LoginLogTypeEnum logType, String openid) {
        // 统一校验用户状态，避免登录方式增加后遗漏
        validateUserStatus(user, mobile, logType);

        // 插入登陆日志
        createLoginLog(user.getId(), mobile, logType, LoginResultEnum.SUCCESS);
        // 创建 Token 令牌
        OAuth2AccessTokenRespDTO accessTokenRespDTO = oauth2TokenApi.createAccessToken(new OAuth2AccessTokenCreateReqDTO()
                .setUserId(user.getId()).setUserType(getUserType().getValue())
                .setClientId(OAuth2ClientConstants.CLIENT_ID_DEFAULT)).getCheckedData();
        // 构建返回结果
        return AuthConvert.INSTANCE.convert(accessTokenRespDTO, openid);
    }

    private void validateUserStatus(MemberUserDO user, String mobile, LoginLogTypeEnum logType) {
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            createLoginLog(user.getId(), mobile, logType, LoginResultEnum.USER_DISABLED);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
    }

    @Override
    public String getSocialAuthorizeUrl(Integer type, String redirectUri) {
        return socialClientApi.getAuthorizeUrl(type, UserTypeEnum.MEMBER.getValue(), redirectUri).getCheckedData();
    }

    private MemberUserDO login0(String mobile, String password) {
        final LoginLogTypeEnum logTypeEnum = LoginLogTypeEnum.LOGIN_MOBILE;
        // 校验账号是否存在
        MemberUserDO user = userService.getUserByMobile(mobile);
        if (user == null) {
            createLoginLog(null, mobile, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (!userService.isPasswordMatch(password, user.getPassword())) {
            createLoginLog(user.getId(), mobile, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 校验是否禁用
        validateUserStatus(user, mobile, logTypeEnum);
        return user;
    }

    private void createLoginLog(Long userId, String mobile, LoginLogTypeEnum logType, LoginResultEnum loginResult) {
        // 插入登录日志
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logType.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(getUserType().getValue());
        reqDTO.setUsername(mobile);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(getClientIP());
        reqDTO.setResult(loginResult.getResult());
        loginLogApi.createLoginLog(reqDTO).checkError();
        // 更新最后登录时间
        if (userId != null && Objects.equals(LoginResultEnum.SUCCESS.getResult(), loginResult.getResult())) {
            userService.updateUserLogin(userId, getClientIP());
        }
    }

    @Override
    public void logout(String token) {
        // 删除访问令牌
        OAuth2AccessTokenRespDTO accessTokenRespDTO = oauth2TokenApi.removeAccessToken(token).getCheckedData();
        if (accessTokenRespDTO == null) {
            return;
        }
        // 删除成功，则记录登出日志
        createLogoutLog(accessTokenRespDTO.getUserId());
    }

    @Override
    public void sendSmsCode(Long userId, AppAuthSmsSendReqVO reqVO) {
        // 图形验证码闸门：**发短信之前**先过。短信是真金白银，这个闸门就是用来挡
        // 「脚本刷短信」的，所以必须卡在这一步，而不是提交登录时再校验。
        // 开关关闭时 CaptchaApi 自己返回 true（放行），本模块不必知道开关的存在 ——
        // 判定只在一处，不会出现「前端弹了滑块、后端没校验」这类错配。
        CaptchaVerificationReqDTO captchaReqDTO = new CaptchaVerificationReqDTO();
        captchaReqDTO.setCaptchaVerification(reqVO.getCaptchaVerification());
        if (!Boolean.TRUE.equals(captchaApi.verification(captchaReqDTO).getCheckedData())) {
            throw exception(AUTH_SMS_CAPTCHA_ERROR);
        }
        // 情况 1：如果是修改手机场景，需要校验新手机号是否已经注册，说明不能使用该手机了
        if (Objects.equals(reqVO.getScene(), SmsSceneEnum.MEMBER_UPDATE_MOBILE.getScene())) {
            MemberUserDO user = userService.getUserByMobile(reqVO.getMobile());
            if (user != null && !Objects.equals(user.getId(), userId)) {
                throw exception(AUTH_MOBILE_USED);
            }
        }
        // 情况 2：如果是重置密码场景，需要校验手机号是存在的
        if (Objects.equals(reqVO.getScene(), SmsSceneEnum.MEMBER_RESET_PASSWORD.getScene())) {
            MemberUserDO user = userService.getUserByMobile(reqVO.getMobile());
            if (user == null) {
                throw exception(USER_MOBILE_NOT_EXISTS);
            }
        }
        // 情况 3：如果是修改密码场景，需要查询手机号，无需前端传递
        if (Objects.equals(reqVO.getScene(), SmsSceneEnum.MEMBER_UPDATE_PASSWORD.getScene())) {
            MemberUserDO user = userService.getUser(userId);
            // TODO 芋艿：后续 member user 手机非强绑定，这块需要做下调整；
            reqVO.setMobile(user.getMobile());
        }

        // 执行发送
        smsCodeApi.sendSmsCode(AuthConvert.INSTANCE.convert(reqVO).setCreateIp(getClientIP())).checkError();
    }

    @Override
    public void validateSmsCode(Long userId, AppAuthSmsValidateReqVO reqVO) {
        smsCodeApi.validateSmsCode(AuthConvert.INSTANCE.convert(reqVO)).checkError();
    }

    @Override
    public AppAuthLoginRespVO refreshToken(String refreshToken) {
        OAuth2AccessTokenRespDTO accessTokenDO = oauth2TokenApi.refreshAccessToken(refreshToken,
                OAuth2ClientConstants.CLIENT_ID_DEFAULT).getCheckedData();
        return AuthConvert.INSTANCE.convert(accessTokenDO, null);
    }

    private void createLogoutLog(Long userId) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(LoginLogTypeEnum.LOGOUT_SELF.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(getUserType().getValue());
        reqDTO.setUsername(getMobile(userId));
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(getClientIP());
        reqDTO.setResult(LoginResultEnum.SUCCESS.getResult());
        loginLogApi.createLoginLog(reqDTO).checkError();
    }

    private String getMobile(Long userId) {
        if (userId == null) {
            return null;
        }
        MemberUserDO user = userService.getUser(userId);
        return user != null ? user.getMobile() : null;
    }

    private UserTypeEnum getUserType() {
        return UserTypeEnum.MEMBER;
    }

}
