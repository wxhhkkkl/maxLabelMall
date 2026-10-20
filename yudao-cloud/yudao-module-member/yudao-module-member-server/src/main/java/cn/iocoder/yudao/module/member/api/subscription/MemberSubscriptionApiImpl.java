package cn.iocoder.yudao.module.member.api.subscription;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.subscription.dto.MemberSubscriptionGrantReqDTO;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class MemberSubscriptionApiImpl implements MemberSubscriptionApi {

    @Resource
    private MemberSubscriptionService subscriptionService;

    @Override
    public CommonResult<Boolean> grant(MemberSubscriptionGrantReqDTO reqDTO) {
        subscriptionService.grant(reqDTO);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> markRefundedByOrder(Long orderId) {
        subscriptionService.markRefundedByOrder(orderId);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> markRefundedByOrderItem(Long orderItemId) {
        subscriptionService.markRefundedByOrderItem(orderItemId);
        return success(true);
    }

}
