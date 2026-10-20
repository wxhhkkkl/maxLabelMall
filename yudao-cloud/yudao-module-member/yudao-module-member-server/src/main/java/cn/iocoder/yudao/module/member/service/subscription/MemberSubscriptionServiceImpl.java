package cn.iocoder.yudao.module.member.service.subscription;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.api.subscription.dto.MemberSubscriptionGrantReqDTO;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.subscription.MemberSubscriptionMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

/**
 * MaxLabel 会员订阅 Service 实现类。
 *
 * 发放 = 授予流水 + 会员行锁；有效/过期由排他 end_time 推导。
 */
@Service
@Validated
public class MemberSubscriptionServiceImpl implements MemberSubscriptionService {

    private static final int MAX_GRANT_MONTHS = 12000;

    @Resource
    private MemberSubscriptionMapper subscriptionMapper;
    @Resource
    private MemberUserMapper memberUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberSubscriptionDO grant(MemberSubscriptionGrantReqDTO reqDTO) {
        validateGrantRequest(reqDTO);
        // 幂等：同一订单项只发放一次
        MemberSubscriptionDO existing = subscriptionMapper.selectBySourceOrderItemId(reqDTO.getOrderItemId());
        if (existing != null) {
            return existing;
        }
        // 会员行锁：并发首购/续费串行化
        MemberUserDO user = memberUserMapper.selectOne(new LambdaQueryWrapperX<MemberUserDO>()
                .eq(MemberUserDO::getId, reqDTO.getMemberId())
                .last("FOR UPDATE"));
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        int count = reqDTO.getCount() == null ? 1 : reqDTO.getCount();
        long months = (long) reqDTO.getPeriodMonths() * count;
        if (months <= 0 || months > MAX_GRANT_MONTHS) {
            throw exception(MEMBER_SUBSCRIPTION_PERIOD_INVALID);
        }
        // 排他 end_time 已是"最后有效日+1天 00:00"，续费起点直接取它的日期，不再 +1 天
        LocalDateTime latestEndTime = subscriptionMapper.selectListByMemberId(reqDTO.getMemberId()).stream()
                .map(MemberSubscriptionDO::getEndTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDate today = LocalDate.now();
        LocalDate startDate = latestEndTime == null ? today
                : latestEndTime.toLocalDate().isAfter(today) ? latestEndTime.toLocalDate() : today;
        LocalDate endDate = startDate.plusMonths(months);

        MemberSubscriptionDO subscription = MemberSubscriptionDO.builder()
                .memberId(reqDTO.getMemberId())
                .planId(reqDTO.getPlanId())
                .spuId(reqDTO.getSpuId())
                .skuId(reqDTO.getSkuId())
                .periodMonths((int) months)
                .startTime(startDate.atStartOfDay())
                .endTime(endDate.plusDays(1).atStartOfDay())
                .sourceOrderId(reqDTO.getOrderId())
                .sourceOrderItemId(reqDTO.getOrderItemId())
                .sourceOrderNo(reqDTO.getOrderNo())
                .refunded(false)
                .build();
        try {
            subscriptionMapper.insert(subscription);
        } catch (DuplicateKeyException duplicate) {
            // 并发回调：另一条路径已插入，返回已有行
            return subscriptionMapper.selectBySourceOrderItemId(reqDTO.getOrderItemId());
        }
        return subscription;
    }

    @Override
    public AppMemberSubscriptionRespVO getCurrent(Long memberId) {
        AppMemberSubscriptionRespVO resp = new AppMemberSubscriptionRespVO();
        resp.setMemberId(memberId);
        resp.setActive(false);
        resp.setRemainingDays(0L);
        List<MemberSubscriptionDO> rows = subscriptionMapper.selectListByMemberId(memberId);
        if (CollUtil.isEmpty(rows)) {
            return resp;
        }
        MemberSubscriptionDO latest = rows.stream()
                .filter(row -> row.getEndTime() != null)
                .max((left, right) -> left.getEndTime().compareTo(right.getEndTime()))
                .orElse(null);
        if (latest == null) {
            return resp;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate endDate = latest.getEndTime().toLocalDate().minusDays(1);
        resp.setActive(latest.getEndTime().isAfter(now));
        resp.setStartDate(latest.getStartTime().toLocalDate());
        resp.setEndDate(endDate);
        resp.setStartTime(latest.getStartTime());
        resp.setEndTime(latest.getEndTime());
        resp.setRemainingDays(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), endDate)));
        resp.setPeriodMonths(latest.getPeriodMonths());
        resp.setSourceOrderNo(latest.getSourceOrderNo());
        return resp;
    }

    @Override
    public PageResult<MemberSubscriptionDO> getPageByMemberId(Long memberId, PageParam pageParam) {
        return subscriptionMapper.selectPageByMemberId(memberId, pageParam);
    }

    @Override
    public Map<Long, MemberSubscriptionDO> getLatestByMemberIds(Collection<Long> memberIds) {
        if (CollUtil.isEmpty(memberIds)) {
            return Collections.emptyMap();
        }
        Map<Long, MemberSubscriptionDO> result = new LinkedHashMap<>();
        for (MemberSubscriptionDO row : subscriptionMapper.selectListByMemberIds(memberIds)) {
            MemberSubscriptionDO current = result.get(row.getMemberId());
            if (current == null || row.getEndTime().isAfter(current.getEndTime())) {
                result.put(row.getMemberId(), row);
            }
        }
        return result;
    }

    @Override
    public void markRefundedByOrder(Long orderId) {
        subscriptionMapper.update(null, new LambdaUpdateWrapper<MemberSubscriptionDO>()
                .eq(MemberSubscriptionDO::getSourceOrderId, orderId)
                .set(MemberSubscriptionDO::getRefunded, true));
    }

    @Override
    public void markRefundedByOrderItem(Long orderItemId) {
        subscriptionMapper.update(null, new LambdaUpdateWrapper<MemberSubscriptionDO>()
                .eq(MemberSubscriptionDO::getSourceOrderItemId, orderItemId)
                .set(MemberSubscriptionDO::getRefunded, true));
    }

    private void validateGrantRequest(MemberSubscriptionGrantReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getMemberId() == null || reqDTO.getOrderItemId() == null
                || reqDTO.getOrderId() == null || reqDTO.getPeriodMonths() == null
                || reqDTO.getPeriodMonths() <= 0) {
            throw exception(MEMBER_SUBSCRIPTION_PERIOD_INVALID);
        }
    }

}
