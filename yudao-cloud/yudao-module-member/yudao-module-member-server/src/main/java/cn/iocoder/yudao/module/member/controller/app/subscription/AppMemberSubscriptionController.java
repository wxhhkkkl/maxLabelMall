package cn.iocoder.yudao.module.member.controller.app.subscription;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 会员订阅")
@RestController
@RequestMapping("/member/subscription")
@Validated
public class AppMemberSubscriptionController {

    @Resource
    private MemberSubscriptionService subscriptionService;

    @GetMapping("/get")
    @Operation(summary = "获得当前会员订阅")
    public CommonResult<AppMemberSubscriptionRespVO> getCurrent() {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        return success(subscriptionService.getCurrent(memberId));
    }

}
