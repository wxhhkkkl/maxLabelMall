package cn.iocoder.yudao.module.member.service.subscription;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.api.subscription.dto.MemberSubscriptionGrantReqDTO;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgLicenseMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgSeatMapper;
import cn.iocoder.yudao.module.member.dal.mysql.subscription.MemberSubscriptionMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.org.MaxLabelOrgStatusEnum;
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
import java.util.Comparator;
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
    @Resource
    private MaxLabelOrgSeatMapper orgSeatMapper;
    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MaxLabelOrgLicenseMapper orgLicenseMapper;

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
        // end_time 是"最后有效日 + 1 天的 00:00"；续费应从**原到期日**（最后有效日）接续，
        // 否则每续一次会多出 1 天，违反 SC-078 的"误差 0 天"。
        LocalDate startDate = latestEndTime == null ? today
                : latestEndTime.toLocalDate().minusDays(1).isAfter(today)
                        ? latestEndTime.toLocalDate().minusDays(1) : today;
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
        resp.setSourceType(1);

        // 1. 个人来源
        MemberSubscriptionDO latestPersonal = subscriptionMapper.selectListByMemberId(memberId).stream()
                .filter(row -> row.getEndTime() != null)
                .max(Comparator.comparing(MemberSubscriptionDO::getEndTime))
                .orElse(null);
        LocalDateTime personalEnd = latestPersonal == null ? null : latestPersonal.getEndTime();

        // 2. 企业来源：有效席位 + 企业启用 + 至少一条未过期批次
        MaxLabelOrgSeatDO seat = orgSeatMapper.selectActiveByMemberId(memberId);
        MaxLabelOrgDO org = seat == null ? null : orgMapper.selectById(seat.getOrgId());
        List<MaxLabelOrgLicenseDO> orgLicenses = org != null
                && MaxLabelOrgStatusEnum.ENABLE.getStatus().equals(org.getStatus())
                ? orgLicenseMapper.selectUnexpiredNonRefundedListByOrgId(org.getId(), LocalDateTime.now())
                : Collections.emptyList();
        LocalDateTime orgEnd = orgLicenses.stream()
                .map(MaxLabelOrgLicenseDO::getEndTime)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        // 3. 取较晚者；并列时以个人为准
        LocalDateTime effectiveEnd = personalEnd == null ? orgEnd
                : orgEnd == null ? personalEnd
                : (orgEnd.isAfter(personalEnd) ? orgEnd : personalEnd);
        if (effectiveEnd == null) {
            return resp;
        }
        boolean orgWins = orgEnd != null && (personalEnd == null || orgEnd.isAfter(personalEnd));
        LocalDate endDate = effectiveEnd.toLocalDate().minusDays(1);
        resp.setActive(effectiveEnd.isAfter(LocalDateTime.now()));
        resp.setEndDate(endDate);
        resp.setEndTime(effectiveEnd);
        resp.setRemainingDays(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), endDate)));
        resp.setSourceType(orgWins ? 2 : 1);
        // 有有效企业席位就返回企业信息；sourceType 只表示谁决定 endDate
        if (orgEnd != null) {
            resp.setOrgId(org.getId());
            resp.setOrgName(org.getName());
        }
        if (orgWins) {
            LocalDateTime orgStart = orgLicenses.stream()
                    .map(MaxLabelOrgLicenseDO::getStartTime)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            resp.setStartTime(orgStart);
            resp.setStartDate(orgStart == null ? null : orgStart.toLocalDate());
            resp.setPeriodMonths(null);
            resp.setSourceOrderNo(null);
        } else if (latestPersonal != null) {
            resp.setStartTime(latestPersonal.getStartTime());
            resp.setStartDate(latestPersonal.getStartTime().toLocalDate());
            resp.setPeriodMonths(latestPersonal.getPeriodMonths());
            resp.setSourceOrderNo(latestPersonal.getSourceOrderNo());
        }
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
        orgLicenseMapper.updateRefundedByOrderId(orderId);
    }

    @Override
    public void markRefundedByOrderItem(Long orderItemId) {
        subscriptionMapper.update(null, new LambdaUpdateWrapper<MemberSubscriptionDO>()
                .eq(MemberSubscriptionDO::getSourceOrderItemId, orderItemId)
                .set(MemberSubscriptionDO::getRefunded, true));
        orgLicenseMapper.updateRefundedByOrderItemId(orderItemId);
    }

    private void validateGrantRequest(MemberSubscriptionGrantReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getMemberId() == null || reqDTO.getOrderItemId() == null
                || reqDTO.getOrderId() == null || reqDTO.getPeriodMonths() == null
                || reqDTO.getPeriodMonths() <= 0) {
            throw exception(MEMBER_SUBSCRIPTION_PERIOD_INVALID);
        }
    }

}
