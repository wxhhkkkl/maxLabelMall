package cn.iocoder.yudao.module.system.api.captcha;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.captcha.dto.CaptchaVerificationReqDTO;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * {@link CaptchaApi} 的实现。
 *
 * ⚠️ **开关只在这里判定**：`yudao.captcha.enable` 关闭时直接返回 true（放行），
 * 调用方（会员模块）不必知道这个开关的存在。这样本地开发/e2e 不受滑块影响，
 * 而生产打开开关后所有调用点自动生效 —— 不会出现「前端弹了滑块、后端没校验」
 * 或反过来的错配。
 */
@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class CaptchaApiImpl implements CaptchaApi {

    @Resource
    private CaptchaService captchaService;

    @Value("${yudao.captcha.enable:true}")
    private Boolean captchaEnable;

    @Override
    public CommonResult<Boolean> verification(CaptchaVerificationReqDTO reqDTO) {
        if (!Boolean.TRUE.equals(captchaEnable)) {
            return success(true);
        }
        // 开关开着但凭据为空（例如绕过前端直接打接口）→ 一律算没通过，
        // 而不是当成「不需要校验」
        if (StrUtil.isEmpty(reqDTO.getCaptchaVerification())) {
            return success(false);
        }
        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaVerification(reqDTO.getCaptchaVerification());
        return success(captchaService.verification(captchaVO).isSuccess());
    }

}
