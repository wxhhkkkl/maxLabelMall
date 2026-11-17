package cn.iocoder.yudao.module.member.service.license;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.license.MemberLicenseGrantDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;
import cn.iocoder.yudao.module.member.dal.mysql.license.MemberLicenseGrantMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgLicenseMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMapper;
import cn.iocoder.yudao.module.member.dal.mysql.subscription.MemberSubscriptionMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.member.enums.LogRecordConstants.*;

/**
 * 授权赠送实现（020）
 *
 * 口径（spec FR-548 ~ FR-555、research R2/R4/R6）：
 * · 个人固定「专业版」、企业固定「企业版」（Q158），请求里不接受版本
 * · 仍有效 → 从原到期日顺延；已过期/无授权 → 从今天起算
 * · 企业送席位加到**当前有效批次**（`getSeatTotal` 取 MAX，加错批次等于没送）
 * · 企业无有效批次时，送席位必须同时给时长（Q157）
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class LicenseGrantServiceImpl implements LicenseGrantService {

    private static final String EDITION_PROFESSIONAL = "professional";
    private static final String EDITION_ENTERPRISE = "enterprise";

    @Resource
    private MemberLicenseGrantMapper grantMapper;
    @Resource
    private MemberSubscriptionMapper subscriptionMapper;
    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MaxLabelOrgLicenseMapper licenseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_LICENSE_GRANT_TYPE, subType = MEMBER_LICENSE_GRANT_SUB_TYPE,
            success = MEMBER_LICENSE_GRANT_SUCCESS, bizNo = "{{#reqVO.targetId}}")
    public LicenseGrantRespVO grant(LicenseGrantReqVO reqVO, Long operatorId) {
        // 1. 理由必填（后端也拦，不能只靠前端）
        if (StrUtil.isBlank(reqVO.getReason())) {
            throw exception(LICENSE_GIFT_REASON_REQUIRED);
        }
        if (StrUtil.length(reqVO.getReason()) > 255) {
            throw exception(LICENSE_GIFT_REASON_REQUIRED);
        }
        // 1.1 连点幂等（T088）：同一对象 + 同一内容 + 同一理由，10 秒内只认第一次
        MemberLicenseGrantDO duplicated = findRecentSameGrant(reqVO);
        if (duplicated != null) {
            log.info("[grant][命中幂等，复用赠送记录({})]", duplicated.getId());
            return buildResp(duplicated.getId(), duplicated.getAfterEndTime(), duplicated.getAfterSeats());
        }
        if (Objects.equals(reqVO.getTargetType(), MemberLicenseGrantDO.TARGET_TYPE_MEMBER)) {
            return grantToMember(reqVO, operatorId);
        }
        if (Objects.equals(reqVO.getTargetType(), MemberLicenseGrantDO.TARGET_TYPE_ORG)) {
            return grantToOrg(reqVO, operatorId);
        }
        throw exception(LICENSE_TARGET_NOT_FOUND);
    }

    /**
     * 连点幂等：同一对象 + 同一内容 + 同一理由，10 秒内视为同一次提交
     *
     * 只做"短时间窗"内的去重 —— 运营真的想连送两次，隔一会儿再提交照样能送。
     */
    private MemberLicenseGrantDO findRecentSameGrant(LicenseGrantReqVO reqVO) {
        return grantMapper.selectOne(new LambdaQueryWrapperX<MemberLicenseGrantDO>()
                .eq(MemberLicenseGrantDO::getTargetType, reqVO.getTargetType())
                .eq(MemberLicenseGrantDO::getTargetId, reqVO.getTargetId())
                .eq(MemberLicenseGrantDO::getContentType, reqVO.getContentType())
                .eqIfPresent(MemberLicenseGrantDO::getMonths, reqVO.getMonths())
                .eqIfPresent(MemberLicenseGrantDO::getSeats, reqVO.getSeats())
                .eq(MemberLicenseGrantDO::getReason, reqVO.getReason().trim())
                .gt(MemberLicenseGrantDO::getCreateTime, LocalDateTime.now().minusSeconds(10))
                .orderByDesc(MemberLicenseGrantDO::getId)
                .last("LIMIT 1"));
    }

    // ==================== 个人：只能送时长 ====================

    private LicenseGrantRespVO grantToMember(LicenseGrantReqVO reqVO, Long operatorId) {
        if (memberUserMapper.selectById(reqVO.getTargetId()) == null) {
            throw exception(LICENSE_TARGET_NOT_FOUND);
        }
        if (!Objects.equals(reqVO.getContentType(), MemberLicenseGrantDO.CONTENT_TYPE_MONTHS)) {
            throw exception(LICENSE_GIFT_CONTENT_INVALID);
        }
        int months = validateMonths(reqVO.getMonths());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentEnd = subscriptionMapper.selectListByMemberId(reqVO.getTargetId()).stream()
                .map(MemberSubscriptionDO::getEndTime).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        long remainingDays = remainingDays(currentEnd, now);
        checkQuota(months, remainingDays);

        // 起点：仍有效 → 原到期日（−1 天，因为库里存次日 00:00）；否则今天
        LocalDate startDate = currentEnd != null && currentEnd.isAfter(now)
                ? currentEnd.toLocalDate().minusDays(1) : now.toLocalDate();
        LocalDateTime newEndTime = startDate.plusMonths(months).plusDays(1).atStartOfDay();

        // 1.1 赠送记录
        MemberLicenseGrantDO grant = MemberLicenseGrantDO.builder()
                .targetType(MemberLicenseGrantDO.TARGET_TYPE_MEMBER)
                .targetId(reqVO.getTargetId())
                .contentType(MemberLicenseGrantDO.CONTENT_TYPE_MONTHS)
                .edition(EDITION_PROFESSIONAL)
                .months(months)
                .reason(reqVO.getReason().trim())
                .beforeEndTime(currentEnd)
                .afterEndTime(newEndTime)
                .operatorId(operatorId)
                .build();
        grantMapper.insert(grant);

        // 1.2 订阅行：来源列全 NULL、licenseGrantId 指向赠送记录（R2）
        MemberSubscriptionDO subscription = MemberSubscriptionDO.builder()
                .memberId(reqVO.getTargetId())
                .edition(EDITION_PROFESSIONAL)
                .periodMonths(months)
                .startTime(startDate.atStartOfDay())
                .endTime(newEndTime)
                .licenseGrantId(grant.getId())
                .refunded(false)
                .build();
        subscriptionMapper.insert(subscription);

        LicenseGrantRespVO resp = new LicenseGrantRespVO();
        resp.setGrantId(grant.getId());
        resp.setAfterEndTime(newEndTime.minusDays(1));
        return resp;
    }

    // ==================== 企业：席位 / 时长 ====================

    private LicenseGrantRespVO grantToOrg(LicenseGrantReqVO reqVO, Long operatorId) {
        MaxLabelOrgDO org = orgMapper.selectById(reqVO.getTargetId());
        if (org == null) {
            throw exception(LICENSE_TARGET_NOT_FOUND);
        }
        if (!Objects.equals(org.getStatus(), 0)) {
            throw exception(LICENSE_ORG_DISABLED);
        }

        LocalDateTime now = LocalDateTime.now();
        List<MaxLabelOrgLicenseDO> activeBatches = licenseMapper.selectList(
                        new LambdaQueryWrapperX<MaxLabelOrgLicenseDO>()
                                .eq(MaxLabelOrgLicenseDO::getOrgId, org.getId())
                                .gt(MaxLabelOrgLicenseDO::getEndTime, now)
                                .eq(MaxLabelOrgLicenseDO::getRefunded, false)
                                .isNull(MaxLabelOrgLicenseDO::getFoldedTime))
                .stream().toList();
        LocalDateTime currentEnd = activeBatches.stream()
                .map(MaxLabelOrgLicenseDO::getEndTime).max(Comparator.naturalOrder()).orElse(null);
        int currentSeats = activeBatches.stream()
                .mapToInt(row -> row.getSeatsTotal() == null ? 0 : row.getSeatsTotal()).max().orElse(0);

        if (Objects.equals(reqVO.getContentType(), MemberLicenseGrantDO.CONTENT_TYPE_SEATS)) {
            int seats = reqVO.getSeats() == null ? 0 : reqVO.getSeats();
            if (seats <= 0) {
                throw exception(LICENSE_GIFT_SEATS_INVALID);
            }
            LocalDateTime newEndTime;
            if (activeBatches.isEmpty()) {
                // 无有效批次：必须同时给时长，否则席位无处生效（Q157）
                if (reqVO.getMonths() == null) {
                    throw exception(LICENSE_GIFT_MONTHS_REQUIRED);
                }
                int months = validateMonths(reqVO.getMonths());
                newEndTime = now.toLocalDate().plusMonths(months).plusDays(1).atStartOfDay();
                MemberLicenseGrantDO grant = insertGrant(reqVO, MemberLicenseGrantDO.CONTENT_TYPE_SEATS,
                        EDITION_ENTERPRISE, months, seats, null, newEndTime, 0, seats, operatorId);
                licenseMapper.insert(MaxLabelOrgLicenseDO.builder()
                        .orgId(org.getId()).edition(EDITION_ENTERPRISE)
                        .periodMonths(months).seatsTotal(seats)
                        .startTime(now.toLocalDate().atStartOfDay()).endTime(newEndTime)
                        .licenseGrantId(grant.getId()).refunded(false)
                        .build());
                return buildResp(grant.getId(), newEndTime, seats);
            }
            // 有有效批次：加到「决定容量」的那一批（MAX(seats_total)，并列取最晚到期）
            MaxLabelOrgLicenseDO target = activeBatches.stream()
                    .max(Comparator.comparing((MaxLabelOrgLicenseDO b) ->
                                    b.getSeatsTotal() == null ? 0 : b.getSeatsTotal())
                            .thenComparing(MaxLabelOrgLicenseDO::getEndTime))
                    .orElseThrow();
            int afterSeats = currentSeats + seats;
            MemberLicenseGrantDO grant = insertGrant(reqVO, MemberLicenseGrantDO.CONTENT_TYPE_SEATS,
                    EDITION_ENTERPRISE, null, seats, currentEnd, currentEnd, currentSeats, afterSeats, operatorId);
            MaxLabelOrgLicenseDO update = new MaxLabelOrgLicenseDO();
            update.setId(target.getId());
            update.setSeatsTotal((target.getSeatsTotal() == null ? 0 : target.getSeatsTotal()) + seats);
            licenseMapper.updateById(update);
            return buildResp(grant.getId(), currentEnd, afterSeats);
        }

        // 送时长
        int months = validateMonths(reqVO.getMonths());
        long remainingDays = remainingDays(currentEnd, now);
        checkQuota(months, remainingDays);
        LocalDate startDate = currentEnd != null && currentEnd.isAfter(now)
                ? currentEnd.toLocalDate().minusDays(1) : now.toLocalDate();
        LocalDateTime newEndTime = startDate.plusMonths(months).plusDays(1).atStartOfDay();

        if (activeBatches.isEmpty()) {
            MemberLicenseGrantDO grant = insertGrant(reqVO, MemberLicenseGrantDO.CONTENT_TYPE_MONTHS,
                    EDITION_ENTERPRISE, months, null, null, newEndTime, null, null, operatorId);
            licenseMapper.insert(MaxLabelOrgLicenseDO.builder()
                    .orgId(org.getId()).edition(EDITION_ENTERPRISE)
                    .periodMonths(months).seatsTotal(0)
                    .startTime(now.toLocalDate().atStartOfDay()).endTime(newEndTime)
                    .licenseGrantId(grant.getId()).refunded(false)
                    .build());
            return buildResp(grant.getId(), newEndTime, 0);
        }
        MaxLabelOrgLicenseDO target = activeBatches.stream()
                .max(Comparator.comparing(MaxLabelOrgLicenseDO::getEndTime)).orElseThrow();
        MemberLicenseGrantDO grant = insertGrant(reqVO, MemberLicenseGrantDO.CONTENT_TYPE_MONTHS,
                EDITION_ENTERPRISE, months, null, currentEnd, newEndTime, null, null, operatorId);
        // 顺延 end_time，并同步 period_months（否则"授 12 个月却多出 3 个月"）
        MaxLabelOrgLicenseDO update = new MaxLabelOrgLicenseDO();
        update.setId(target.getId());
        update.setEndTime(newEndTime);
        update.setPeriodMonths((target.getPeriodMonths() == null ? 0 : target.getPeriodMonths()) + months);
        licenseMapper.updateById(update);
        return buildResp(grant.getId(), newEndTime, currentSeats);
    }

    // ==================== 工具 ====================

    private MemberLicenseGrantDO insertGrant(LicenseGrantReqVO reqVO, int contentType, String edition,
                                             Integer months, Integer seats, LocalDateTime beforeEnd,
                                             LocalDateTime afterEnd, Integer beforeSeats, Integer afterSeats,
                                             Long operatorId) {
        MemberLicenseGrantDO grant = MemberLicenseGrantDO.builder()
                .targetType(MemberLicenseGrantDO.TARGET_TYPE_ORG)
                .targetId(reqVO.getTargetId())
                .contentType(contentType)
                .edition(edition)
                .months(months)
                .seats(seats)
                .reason(reqVO.getReason().trim())
                .beforeEndTime(beforeEnd)
                .afterEndTime(afterEnd)
                .beforeSeats(beforeSeats)
                .afterSeats(afterSeats)
                .operatorId(operatorId)
                .build();
        grantMapper.insert(grant);
        return grant;
    }

    private LicenseGrantRespVO buildResp(Long grantId, LocalDateTime afterEndTime, Integer afterSeats) {
        LicenseGrantRespVO resp = new LicenseGrantRespVO();
        resp.setGrantId(grantId);
        resp.setAfterEndTime(afterEndTime == null ? null : afterEndTime.minusDays(1));
        resp.setAfterSeats(afterSeats);
        return resp;
    }

    private int validateMonths(Integer months) {
        if (months == null || !LicenseGiftQuota.MONTH_TIERS.contains(months)) {
            throw exception(LICENSE_GIFT_MONTHS_INVALID);
        }
        return months;
    }

    /** 剩余天数（到期日按"最后有效日"算） */
    private long remainingDays(LocalDateTime endTime, LocalDateTime now) {
        if (endTime == null) {
            return 0;
        }
        LocalDate end = endTime.toLocalDate().minusDays(1);
        return Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), end));
    }

    private void checkQuota(int months, long remainingDays) {
        int maxGift = LicenseGiftQuota.maxGiftMonths(remainingDays);
        if (months > maxGift) {
            throw exception(LICENSE_GIFT_LIMIT_EXCEEDED, maxGift);
        }
    }

    @Override
    public PageResult<MemberLicenseGrantDO> getGrantPage(LicenseGrantPageReqVO reqVO) {
        return grantMapper.selectPage(reqVO);
    }

}
