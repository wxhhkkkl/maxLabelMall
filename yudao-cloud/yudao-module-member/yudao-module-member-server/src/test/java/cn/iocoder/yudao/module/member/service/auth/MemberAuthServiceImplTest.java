package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthSmsSendReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.captcha.CaptchaApi;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.AUTH_SMS_CAPTCHA_ERROR;
import static cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum.MEMBER_LOGIN;
import static cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum.MEMBER_UPDATE_PASSWORD;
import static org.mockito.ArgumentMatchers.any;
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

}
