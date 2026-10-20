package cn.iocoder.yudao.module.member.service.subscription;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.subscription.dto.MemberSubscriptionGrantReqDTO;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;

import java.util.Collection;
import java.util.Map;

public interface MemberSubscriptionService {

    MemberSubscriptionDO grant(MemberSubscriptionGrantReqDTO reqDTO);

    AppMemberSubscriptionRespVO getCurrent(Long memberId);

    PageResult<MemberSubscriptionDO> getPageByMemberId(Long memberId, PageParam pageParam);

    /**
     * 批量取每个会员的最新一段订阅（用于会员列表批量填充，避免 N+1）。
     */
    Map<Long, MemberSubscriptionDO> getLatestByMemberIds(Collection<Long> memberIds);

    void markRefundedByOrder(Long orderId);

    void markRefundedByOrderItem(Long orderItemId);

}
