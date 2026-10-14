package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthSmsSendReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthWechatCallbackReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthWechatCompleteReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthWechatScanCreateRespVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthWechatScanQueryRespVO;
import cn.iocoder.yudao.module.member.controller.app.user.vo.AppMemberUserUpdateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.auth.bo.WechatLoginTicketBO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.captcha.CaptchaApi;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_SMS_CAPTCHA_ERROR;
import static cn.iocoder.yudao.module.member.service.auth.WechatLoginTicketStatusEnum.*;
import static cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum.MEMBER_LOGIN;
import static cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum.MEMBER_UPDATE_PASSWORD;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemberAuthServiceImpl} 的单元测试 —— 只覆盖本次新增的**图形验证码闸门**。
 *
 * ⚠️ 这个文件存在的意义就是钉住一件事：**滑块没过，短信一个字节都不该发出去**。
 * 图形验证码的价值全在「服务端强制校验」上 —— 只在前端画个滑块、服务端不校验，
 * 机器人绕过界面直接调 `/app-api/member/auth/send-sms-code` 就白拿了。
 * 所以断言的重点不是「抛了异常」，而是**`smsCodeApi.sendSmsCode` 从未被调用**。
 */
@Import(MemberAuthServiceImpl.class)
public class MemberAuthServiceImplTest extends BaseDbUnitTest {

    @Resource
    private MemberAuthServiceImpl authService;

    @MockitoBean
    private MemberUserService userService;
    @MockitoBean
    private SmsCodeApi smsCodeApi;
    @MockitoBean
    private LoginLogApi loginLogApi;
    @MockitoBean
    private SocialUserApi socialUserApi;
    @MockitoBean
    private SocialClientApi socialClientApi;
    @MockitoBean
    private OAuth2TokenCommonApi oauth2TokenApi;
    @MockitoBean
    private CaptchaApi captchaApi;
    @MockitoBean
    private WechatLoginTicketService wechatLoginTicketService;

    private static AppAuthSmsSendReqVO buildReqVO(String mobile, Integer scene, String captchaVerification) {
        AppAuthSmsSendReqVO reqVO = new AppAuthSmsSendReqVO();
        reqVO.setMobile(mobile);
        reqVO.setScene(scene);
        reqVO.setCaptchaVerification(captchaVerification);
        return reqVO;
    }

    @Test
    public void testSendSmsCode_captchaFailed_neverSendsSms() {
        // 滑块没通过：Api 返回 false
        when(captchaApi.verification(any())).thenReturn(success(false));

        // 调用 + 断言：抛会员自己的错误码，而不是把控制权交给下游
        assertServiceException(() -> authService.sendSmsCode(null,
                buildReqVO("15601691234", MEMBER_LOGIN.getScene(), "bad-verification")),
                AUTH_SMS_CAPTCHA_ERROR);
        // ⚠️ 关键断言：短信**一个都没发**
        verify(smsCodeApi, never()).sendSmsCode(any());
    }

    @Test
    public void testSendSmsCode_captchaPassed_forwardsVerificationAndSends() {
        when(captchaApi.verification(any())).thenReturn(success(true));
        when(smsCodeApi.sendSmsCode(any())).thenReturn(success(true));

        authService.sendSmsCode(null, buildReqVO("15601691234", MEMBER_LOGIN.getScene(), "good-verification"));

        // 滑块凭据要**原样**转给 Api —— 丢了它后端就会当成「没通过」
        verify(captchaApi).verification(org.mockito.ArgumentMatchers.argThat(
                dto -> "good-verification".equals(dto.getCaptchaVerification())));
        verify(smsCodeApi).sendSmsCode(any());
    }

    @Test
    public void testSendSmsCode_checksCaptchaForUpdatePasswordSceneToo() {
        // 改密场景（scene 3）手机号从登录用户取，同样要过滑块
        MemberUserDO user = new MemberUserDO();
        user.setId(1L);
        user.setMobile("15601691234");
        when(userService.getUser(1L)).thenReturn(user);
        when(captchaApi.verification(any())).thenReturn(success(false));

        assertServiceException(() -> authService.sendSmsCode(1L,
                buildReqVO(null, MEMBER_UPDATE_PASSWORD.getScene(), "bad-verification")),
                AUTH_SMS_CAPTCHA_ERROR);
        verify(smsCodeApi, never()).sendSmsCode(any());
    }

    @Test
    public void testSendSmsCode_captchaDisabledBySwitch_passesThrough() {
        // 开关关闭时由 CaptchaApi 自己放行（返回 true），会员侧不改行为 —— 本地/e2e 因此不受影响
        when(captchaApi.verification(any())).thenReturn(success(true));
        when(smsCodeApi.sendSmsCode(any())).thenReturn(success(true));

        authService.sendSmsCode(null, buildReqVO("15601691234", MEMBER_LOGIN.getScene(), null));

        verify(smsCodeApi).sendSmsCode(any());
    }

    // ========== 微信扫码登录（T344 / T345 / T346） ==========

    private static final Integer WECHAT_MP = SocialTypeEnum.WECHAT_MP.getType();

    private static WechatLoginTicketBO buildTicket(String ticket, String state, Integer status) {
        return WechatLoginTicketBO.builder().ticket(ticket).state(state).status(status).build();
    }

    private static AppAuthWechatCallbackReqVO buildCallback(String code, String state) {
        AppAuthWechatCallbackReqVO reqVO = new AppAuthWechatCallbackReqVO();
        reqVO.setCode(code);
        reqVO.setState(state);
        return reqVO;
    }

    @Test
    public void testCreateWechatScanTicket_keepsJustauthStateAndStoresItInTicket() {
        // ⚠️ 这条用例的方向是**反**的，别照着直觉改回去：
        //    justauth 在生成授权地址时会自己生成 state 并**缓存**起来，回调时按它比对。
        //    所以我们必须"**用它给的**"，而不是自己另生成一个替换掉 ——
        //    替换了的话它会抛 `Illegal state [WECHAT_MP]`（2026-10-14 线上实测踩过）。
        when(wechatLoginTicketService.create("SYSTEM_RANDOM_STATE"))
                .thenReturn(buildTicket("t-1", "SYSTEM_RANDOM_STATE", WAITING_SCAN.getStatus()));
        when(wechatLoginTicketService.getTtlSeconds()).thenReturn(300L);
        when(socialClientApi.getAuthorizeUrl(eq(WECHAT_MP), eq(UserTypeEnum.MEMBER.getValue()),
                eq("https://h5/wechat")))
                .thenReturn(success("https://open.weixin.qq.com/connect/oauth2/authorize"
                        + "?appid=wx1&redirect_uri=x&response_type=code&scope=snsapi_userinfo"
                        + "&state=SYSTEM_RANDOM_STATE#wechat_redirect"));

        AppAuthWechatScanCreateRespVO resp = authService.createWechatScanTicket("https://h5/wechat");

        assertEquals("t-1", resp.getTicket());
        // 二维码里的 state **原样保留**（justauth 缓存的就是它）
        assertTrue(resp.getQrContent().contains("state=SYSTEM_RANDOM_STATE"),
                "state 被换掉了 —— justauth 会抛 Illegal state。实际：" + resp.getQrContent());
        // 而它被**存进票据**，于是微信回调带回来时，我们能按 state 找回这张票据
        verify(wechatLoginTicketService).create("SYSTEM_RANDOM_STATE");
        assertEquals(300, resp.getExpiresIn().intValue());
    }

    @Test
    public void testCreateWechatScanTicket_authorizeUrlWithoutState_throws() {
        // 授权地址形状变了就当场炸，别发一张注定回调失败出去的二维码
        when(socialClientApi.getAuthorizeUrl(any(), any(), any()))
                .thenReturn(success("https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx1"));

        assertThrows(IllegalStateException.class,
                () -> authService.createWechatScanTicket("https://h5/wechat"));
    }

    @Test
    public void testHandleWechatCallback_notBound_onlyIdentifies() {
        when(wechatLoginTicketService.getByState("s-1")).thenReturn(buildTicket("t-1", "s-1", WAITING_SCAN.getStatus()));
        when(socialUserApi.getSocialUserByCode(UserTypeEnum.MEMBER.getValue(), WECHAT_MP, "c-1", "s-1"))
                .thenReturn(success(new SocialUserRespDTO().setOpenid("o-1").setNickname("张三").setAvatar("a.png")));

        authService.handleWechatCallback(buildCallback("c-1", "s-1"));

        // 只识别身份
        verify(wechatLoginTicketService).markWaitingProfile("s-1", "o-1", "张三", "a.png");
        // ⚠️⚠️ 本用例的核心（FR-132 / FR-150 / Q26）：这一步**绝不能**建会员、建绑定、发令牌
        verify(userService, never()).createUser(any(), any(), any(), any());
        verify(userService, never()).createUserIfAbsent(any(), any(), any());
        verify(socialUserApi, never()).bindSocialUser(any());
        verify(socialUserApi, never()).bindSocialUserByOpenid(any());
        verify(oauth2TokenApi, never()).createAccessToken(any());
        verify(wechatLoginTicketService, never()).markCompleted(any(), any(), any(), any());
    }

    @Test
    public void testHandleWechatCallback_alreadyBoundWithMobile_completesDirectly() {
        when(wechatLoginTicketService.getByState("s-1")).thenReturn(buildTicket("t-1", "s-1", WAITING_SCAN.getStatus()));
        when(socialUserApi.getSocialUserByCode(UserTypeEnum.MEMBER.getValue(), WECHAT_MP, "c-1", "s-1"))
                .thenReturn(success(new SocialUserRespDTO().setOpenid("o-1").setUserId(42L)));
        when(userService.getUser(42L)).thenReturn(new MemberUserDO().setId(42L).setMobile("13800138000")
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(oauth2TokenApi.createAccessToken(any())).thenReturn(success(new OAuth2AccessTokenRespDTO()
                .setAccessToken("at-1").setRefreshToken("rt-1")
                .setExpiresTime(LocalDateTime.now().plusHours(1))));
        // 发令牌那条路会顺手写登录日志（createTokenAfterLoginSuccess 里），得给它一个返回值
        when(loginLogApi.createLoginLog(any())).thenReturn(success(true));

        authService.handleWechatCallback(buildCallback("c-1", "s-1"));

        // 已绑定 + 有手机号 → 直接完成登录（FR-133），不要求补全
        verify(wechatLoginTicketService).markCompleted("t-1", 42L, "at-1", "rt-1");
    }

    @Test
    public void testQueryWechatScanTicket_completed_returnsTokenAndConsumesTicket() {
        WechatLoginTicketBO ticket = buildTicket("t-1", "s-1", COMPLETED.getStatus());
        ticket.setMemberId(42L);
        ticket.setAccessToken("at-1");
        ticket.setRefreshToken("rt-1");
        when(wechatLoginTicketService.get("t-1")).thenReturn(ticket);

        AppAuthWechatScanQueryRespVO resp = authService.queryWechatScanTicket("t-1");

        assertEquals("已完成", resp.getStatus());
        assertEquals("at-1", resp.getAccessToken());
        assertEquals("rt-1", resp.getRefreshToken());
        // 一次性：取走即失效（FR-149）
        verify(wechatLoginTicketService).remove("t-1");
    }

    @Test
    public void testQueryWechatScanTicket_waitingProfile_hasNoToken() {
        WechatLoginTicketBO ticket = buildTicket("t-1", "s-1", WAITING_PROFILE.getStatus());
        ticket.setWxNickname("张三");
        ticket.setWxAvatar("a.png");
        when(wechatLoginTicketService.get("t-1")).thenReturn(ticket);

        AppAuthWechatScanQueryRespVO resp = authService.queryWechatScanTicket("t-1");

        assertEquals("待补全", resp.getStatus());
        assertEquals("张三", resp.getWxNickname());
        assertEquals("a.png", resp.getWxAvatar());
        // ⚠️ FR-150：待补全时**绝不能**有令牌
        assertNull(resp.getAccessToken());
        assertNull(resp.getRefreshToken());
        verify(wechatLoginTicketService, never()).remove(any());
    }

    @Test
    public void testQueryWechatScanTicket_notFound_isExpired() {
        when(wechatLoginTicketService.get("t-x")).thenReturn(null);

        assertEquals("已过期", authService.queryWechatScanTicket("t-x").getStatus());
    }

    @Test
    public void testQueryWechatScanTicket_abandoned() {
        when(wechatLoginTicketService.get("t-1")).thenReturn(buildTicket("t-1", "s-1", ABANDONED.getStatus()));

        assertEquals("已作废", authService.queryWechatScanTicket("t-1").getStatus());
    }

    // ========== 提交补全页（T351 / T354 / T355） ==========

    private static AppAuthWechatCompleteReqVO buildComplete(String mobile, String company) {
        AppAuthWechatCompleteReqVO reqVO = new AppAuthWechatCompleteReqVO();
        reqVO.setTicket("t-1");
        reqVO.setMobile(mobile);
        reqVO.setSmsCode("1234");
        reqVO.setCompany(company);
        return reqVO;
    }

    /** 票据要处于「待补全」且带着扫码者的 openid —— 这是提交补全的前置状态 */
    private void givenWaitingProfileTicket() {
        WechatLoginTicketBO ticket = buildTicket("t-1", "s-1", WAITING_PROFILE.getStatus());
        ticket.setOpenid("o-1");
        ticket.setWxNickname("微信昵称");
        ticket.setWxAvatar("wx-avatar.png");
        when(wechatLoginTicketService.get("t-1")).thenReturn(ticket);
    }

    private void givenTokenIssuable() {
        when(oauth2TokenApi.createAccessToken(any())).thenReturn(success(new OAuth2AccessTokenRespDTO()
                .setAccessToken("at-1").setRefreshToken("rt-1")
                .setExpiresTime(LocalDateTime.now().plusHours(1))));
        when(loginLogApi.createLoginLog(any())).thenReturn(success(true));
    }

    @Test
    public void testCompleteWechatScan_newMobile_createsUserBindsAndIssuesToken() {
        givenWaitingProfileTicket();
        when(smsCodeApi.useSmsCode(any())).thenReturn(success(true));
        // 手机号**没注册过** → 建号
        when(userService.getUserByMobile("13800138000")).thenReturn(null);
        when(userService.createUserIfAbsent(eq("13800138000"), any(), any()))
                .thenReturn(new MemberUserDO().setId(42L).setMobile("13800138000")
                        .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(socialUserApi.bindSocialUserByOpenid(any())).thenReturn(success("o-1"));
        givenTokenIssuable();

        authService.completeWechatScan(buildComplete("13800138000", "某某印刷厂"));

        verify(userService).createUserIfAbsent(eq("13800138000"), any(), any());
        // 新号：把微信带来的昵称/头像与客户填的公司名字一并落上
        ArgumentCaptor<AppMemberUserUpdateReqVO> profile = ArgumentCaptor.forClass(AppMemberUserUpdateReqVO.class);
        verify(userService).updateUser(eq(42L), profile.capture());
        assertEquals("微信昵称", profile.getValue().getNickname());
        assertEquals("wx-avatar.png", profile.getValue().getAvatar());
        assertEquals("某某印刷厂", profile.getValue().getCompany());
        // 绑定 + 标完成（令牌交给电脑端轮询取）
        verify(socialUserApi).bindSocialUserByOpenid(any());
        verify(wechatLoginTicketService).markCompleted("t-1", 42L, "at-1", "rt-1");
    }

    @Test
    public void testCompleteWechatScan_existingMobile_reusesWithoutCreating() {
        givenWaitingProfileTicket();
        when(smsCodeApi.useSmsCode(any())).thenReturn(success(true));
        // 这个手机号**已经是本站会员**（SC-050：这一条绝不能再建一个号）
        when(userService.getUserByMobile("13800138000"))
                .thenReturn(new MemberUserDO().setId(7L).setMobile("13800138000")
                        .setNickname("老客户").setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(socialUserApi.bindSocialUserByOpenid(any())).thenReturn(success("o-1"));
        givenTokenIssuable();

        authService.completeWechatScan(buildComplete("13800138000", null));

        verify(userService, never()).createUserIfAbsent(any(), any(), any());
        verify(socialUserApi).bindSocialUserByOpenid(any());
        verify(wechatLoginTicketService).markCompleted("t-1", 7L, "at-1", "rt-1");
    }

    @Test
    public void testCompleteWechatScan_existingMobile_neverOverwritesNicknameOrAvatar() {
        givenWaitingProfileTicket();
        when(smsCodeApi.useSmsCode(any())).thenReturn(success(true));
        when(userService.getUserByMobile("13800138000"))
                .thenReturn(new MemberUserDO().setId(7L).setMobile("13800138000")
                        .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(socialUserApi.bindSocialUserByOpenid(any())).thenReturn(success("o-1"));
        givenTokenIssuable();

        authService.completeWechatScan(buildComplete("13800138000", "某某印刷厂"));

        // 老客户只补**公司名称** —— 昵称与头像一个都不能动（那是人家自己设过的）
        ArgumentCaptor<AppMemberUserUpdateReqVO> profile = ArgumentCaptor.forClass(AppMemberUserUpdateReqVO.class);
        verify(userService).updateUser(eq(7L), profile.capture());
        assertEquals("某某印刷厂", profile.getValue().getCompany());
        assertNull(profile.getValue().getNickname(), "不许覆盖老客户的昵称");
        assertNull(profile.getValue().getAvatar(), "不许覆盖老客户的头像");
    }

    @Test
    public void testCompleteWechatScan_smsCodeInvalid_nothingHappens() {
        givenWaitingProfileTicket();
        // 验证码没过
        when(smsCodeApi.useSmsCode(any())).thenThrow(
                new ServiceException(1_002_014_001, "验证码已过期"));

        assertThrows(Exception.class, () -> authService.completeWechatScan(buildComplete("13800138000", "某某印刷厂")));

        // ⚠️⚠️ 本用例的核心（FR-147 / SC-054）：验证码不过时**三个增量全为 0**
        verify(userService, never()).createUserIfAbsent(any(), any(), any());
        verify(socialUserApi, never()).bindSocialUserByOpenid(any());
        verify(oauth2TokenApi, never()).createAccessToken(any());
        verify(wechatLoginTicketService, never()).markCompleted(any(), any(), any(), any());
    }

    @Test
    public void testCompleteWechatScan_memberAlreadyBoundOtherWechat_rejects() {
        givenWaitingProfileTicket();
        when(smsCodeApi.useSmsCode(any())).thenReturn(success(true));
        when(userService.getUserByMobile("13800138000"))
                .thenReturn(new MemberUserDO().setId(7L).setMobile("13800138000")
                        .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        // 该会员已经绑过**另一个**微信 → 服务端拒绝（Q21=C / FR-146）
        when(socialUserApi.bindSocialUserByOpenid(any())).thenReturn(
                CommonResult.error(1_002_018_002, "该账号已绑定其他微信"));

        assertThrows(Exception.class, () -> authService.completeWechatScan(buildComplete("13800138000", null)));

        // 被拒绝时**不发令牌、不标完成** —— 否则客户会进入一个"登录了但没绑上"的怪状态
        verify(oauth2TokenApi, never()).createAccessToken(any());
        verify(wechatLoginTicketService, never()).markCompleted(any(), any(), any(), any());
    }

    @Test
    public void testCompleteWechatScan_ticketExpired_throws() {
        when(wechatLoginTicketService.get("t-1")).thenReturn(null);

        assertThrows(Exception.class, () -> authService.completeWechatScan(buildComplete("13800138000", null)));

        verify(userService, never()).createUserIfAbsent(any(), any(), any());
    }

    @Test
    public void testAbandonWechatScanTicket_marksAbandoned() {
        authService.abandonWechatScanTicket("t-1");

        verify(wechatLoginTicketService).abandon("t-1");
    }

}
