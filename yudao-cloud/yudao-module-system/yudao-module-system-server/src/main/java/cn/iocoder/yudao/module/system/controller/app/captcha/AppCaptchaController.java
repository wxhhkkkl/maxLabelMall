package cn.iocoder.yudao.module.system.controller.app.captcha;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.controller.app.captcha.vo.AppCaptchaCheckReqVO;
import cn.iocoder.yudao.module.system.controller.app.captcha.vo.AppCaptchaCheckRespVO;
import cn.iocoder.yudao.module.system.controller.app.captcha.vo.AppCaptchaGetReqVO;
import cn.iocoder.yudao.module.system.controller.app.captcha.vo.AppCaptchaGetRespVO;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.CAPTCHA_GET_FAILED;

/**
 * 用户 App 的图形验证码（滑块）接口。
 *
 * 与管理端的 `adminCaptchaController` 是同一套 aj-captcha，区别只有两点：
 *   1. 挂在 `/app-api` 下（包名落在 `**.controller.app.**` 即自动获得该前缀），C 端因此
 *      不必去碰 `/admin-api`（宪法：C 端只走 `/app-api`）；
 *   2. **用项目的 `CommonResult` 信封**（见 {@link AppCaptchaGetRespVO} 的说明）。
 *
 * ⚠️ 两个接口都必须 `@TenantIgnore`：它们发生在登录**之前**，请求里没有可靠的租户上下文，
 * 而 aj-captcha 的缓存 key 依赖同一浏览器会话内一致（管理端同款接口也是这么标的）。
 */
@Tag(name = "用户 App - 图形验证码")
@RestController("appCaptchaController") // 与管理端的 adminCaptchaController 区分开
@RequestMapping("/system/captcha")
@Validated
public class AppCaptchaController {

    @Resource
    private CaptchaService captchaService;

    @Value("${yudao.captcha.enable:true}")
    private Boolean captchaEnable;

    @GetMapping("/enable")
    @Operation(summary = "图形验证码是否开启", description = "前端据此决定要不要在发短信前弹滑块")
    @PermitAll
    @TenantIgnore
    public CommonResult<Boolean> enable() {
        return success(captchaEnable);
    }

    @PostMapping("/get")
    @Operation(summary = "获得图形验证码")
    @PermitAll
    @TenantIgnore
    public CommonResult<AppCaptchaGetRespVO> get(@Valid @RequestBody AppCaptchaGetReqVO reqVO,
                                                 HttpServletRequest request) {
        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaType(reqVO.getCaptchaType());
        // 与管理端一致：把「IP + UA」作为滑块二次校验的绑定标识
        captchaVO.setBrowserInfo(getRemoteId(request));

        ResponseModel response = captchaService.get(captchaVO);
        if (!response.isSuccess()) {
            throw exception(CAPTCHA_GET_FAILED, response.getRepMsg());
        }
        // aj-captcha 在成功时把本次的 CaptchaVO 放进 repData
        CaptchaVO data = (CaptchaVO) response.getRepData();
        return success(new AppCaptchaGetRespVO(data.getOriginalImageBase64(), data.getJigsawImageBase64(),
                data.getToken(), data.getSecretKey()));
    }

    @PostMapping("/check")
    @Operation(summary = "校验图形验证码")
    @PermitAll
    @TenantIgnore
    public CommonResult<AppCaptchaCheckRespVO> check(@Valid @RequestBody AppCaptchaCheckReqVO reqVO,
                                                     HttpServletRequest request) {
        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaType(reqVO.getCaptchaType());
        captchaVO.setToken(reqVO.getToken());
        captchaVO.setPointJson(reqVO.getPointJson());
        captchaVO.setBrowserInfo(getRemoteId(request));

        ResponseModel response = captchaService.check(captchaVO);
        return success(new AppCaptchaCheckRespVO(response.isSuccess(), response.getRepMsg()));
    }

    /** 与管理端 `CaptchaController.getRemoteId` 同款：用 IP + UA 标记这个浏览器会话 */
    private static String getRemoteId(HttpServletRequest request) {
        String ip = ServletUtils.getClientIP(request);
        String ua = request.getHeader("user-agent");
        return ip != null ? ip + ua : request.getRemoteAddr() + ua;
    }

}
