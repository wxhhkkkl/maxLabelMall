package cn.iocoder.yudao.module.system.api.captcha;

import cn.hutool.core.util.ReflectUtil;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.api.captcha.dto.CaptchaVerificationReqDTO;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CaptchaApiImpl} 的单元测试 —— 只覆盖「开关到底在哪一处生效」。
 *
 * ⚠️ 这里是**唯一**判定 `yudao.captcha.enable` 的地方，所以它的行为直接决定：
 *   · 关闭时：前端不弹滑块也能正常发短信（本地开发与 e2e 依赖这一点）；
 *   · 打开时：凭据为空（= 绕过前端直接打接口）**必须算不通过**，不能当成「不需要校验」。
 */
@Import(CaptchaApiImpl.class)
public class CaptchaApiImplTest extends BaseDbUnitTest {

    @Resource
    private CaptchaApiImpl captchaApi;

    @MockitoBean
    private CaptchaService captchaService;

    private void setEnable(Boolean enable) {
        ReflectUtil.setFieldValue(captchaApi, "captchaEnable", enable);
    }

    private static CaptchaVerificationReqDTO buildReqDTO(String verification) {
        CaptchaVerificationReqDTO reqDTO = new CaptchaVerificationReqDTO();
        reqDTO.setCaptchaVerification(verification);
        return reqDTO;
    }

    @Test
    public void testVerification_disabled_passesThroughWithoutTouchingCaptcha() {
        setEnable(false);

        assertTrue(captchaApi.verification(buildReqDTO(null)).getCheckedData());
        assertTrue(captchaApi.verification(buildReqDTO("whatever")).getCheckedData());
        // 开关关掉时不该去问 aj-captcha —— 否则关开关也拦人
        verify(captchaService, never()).verification(any());
    }

    @Test
    public void testVerification_enabledButBlank_failsWithoutTouchingCaptcha() {
        setEnable(true);

        assertFalse(captchaApi.verification(buildReqDTO(null)).getCheckedData());
        assertFalse(captchaApi.verification(buildReqDTO("")).getCheckedData());
        // 空凭据直接否掉，不必（也不该）把它喂给 aj-captcha
        verify(captchaService, never()).verification(any());
    }

    @Test
    public void testVerification_enabled_forwardsVerificationAndReturnsResult() {
        setEnable(true);
        when(captchaService.verification(any())).thenReturn(ResponseModel.success());

        assertTrue(captchaApi.verification(buildReqDTO("the-token---point-json")).getCheckedData());
        // 凭据要原样转发（丢了它 aj-captcha 也解不出来）
        verify(captchaService).verification(org.mockito.ArgumentMatchers.argThat(
                (CaptchaVO vo) -> "the-token---point-json".equals(vo.getCaptchaVerification())));
    }

    @Test
    public void testVerification_enabledButCaptchaRejects_returnsFalse() {
        setEnable(true);
        when(captchaService.verification(any())).thenReturn(ResponseModel.errorMsg("验证码错误"));

        assertFalse(captchaApi.verification(buildReqDTO("bad")).getCheckedData());
    }

}
