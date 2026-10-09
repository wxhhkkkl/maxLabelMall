package cn.iocoder.yudao.module.system.api.captcha;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.captcha.dto.CaptchaVerificationReqDTO;
import cn.iocoder.yudao.module.system.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

/**
 * 图形验证码（滑块）的 RPC 服务。
 *
 * 为什么要用它，而不是让别的模块直接注入 aj-captcha 的 `CaptchaService`：
 * 那个依赖只声明在 `yudao-module-system-server` 的 pom 里，别的模块（如 member）
 * **编译期 classpath 上根本没有 `com.anji.captcha.*`**，想 import 也 import 不到。
 *
 * ⚠️ 本接口刻意**不暴露 aj-captcha 的任何类型**（system-api 没有该依赖），
 * 只用 `String` 进、`Boolean` 出。
 *
 * ⚠️ 开关 `yudao.captcha.enable` 的判定**只在实现类里做一处**：关闭时直接返回
 * true（放行）。调用方不需要、也不应该自己判断这个开关。
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 图形验证码")
public interface CaptchaApi {

    String PREFIX = ApiConstants.PREFIX + "/captcha";

    @PostMapping(PREFIX + "/verification")
    @Operation(summary = "校验图形验证码", description = "开关关闭时一律放行；凭据为空或校验不过返回 false")
    CommonResult<Boolean> verification(@Valid @RequestBody CaptchaVerificationReqDTO reqDTO);

}
