package cn.iocoder.yudao.module.member.service.license;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseCustomerIdRow;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseCustomerPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseCustomerRespVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGiftTargetPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGiftTargetRespVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGiftTargetRow;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseSummaryRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.license.MemberLicenseGrantDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.license.LicenseStatisticsMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgLicenseMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgSeatMapper;
import cn.iocoder.yudao.module.member.dal.mysql.subscription.MemberSubscriptionMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 授权看板查询实现（020）
 *
 * ⚠️ 统计与分页**全部下推到 SQL**（{@link LicenseStatisticsMapper}）：
 * 计划里明确"统计必须一次查询出数，禁止先全量取回再在内存里过滤"（T086）。
 * 服务层只做两件事：把 SQL 出来的这一页 id 装配成展示行；把天数/额度算出来。
 *
 * 口径详见 `specs/020-license-dashboard/data-model.md` §4。
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class LicenseQueryServiceImpl implements LicenseQueryService {

    private static final String SOURCE_ORDER = "ORDER";
    private static final String SOURCE_GRANT = "GRANT";
    private static final String SOURCE_ORG_SEAT = "ORG_SEAT";

    @Resource
    private LicenseStatisticsMapper statisticsMapper;
    @Resource
    private MemberSubscriptionMapper subscriptionMapper;
    @Resource
    private MaxLabelOrgLicenseMapper licenseMapper;
    @Resource
    private MaxLabelOrgSeatMapper seatMapper;
    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MemberUserMapper memberUserMapper;

    // ==================== 总览 ====================

    @Override
    public LicenseSummaryRespVO getSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yearStart = LocalDate.of(now.getYear(), 1, 1).atStartOfDay();

        LicenseSummaryRespVO resp = new LicenseSummaryRespVO();
        resp.setYear(now.getYear());
        resp.setPersonalActiveCount(statisticsMapper.countActivePersonalMembers(now));
        resp.setPersonalSeatOnlyCount(statisticsMapper.countSeatOnlyMembers(now));
        resp.setOrgActiveCount(statisticsMapper.countActiveOrgs(now));
        resp.setOrgSeatTotalCount(statisticsMapper.sumActiveOrgSeats(now));
        resp.setOrgSeatActiveCount(statisticsMapper.countActiveOrgSeats(now));

        int personalNew = statisticsMapper.countPersonalNew(yearStart);
        int personalRenew = statisticsMapper.countPersonalRenew(yearStart);
        int orgNew = statisticsMapper.countOrgNew(yearStart);
        int orgRenew = statisticsMapper.countOrgRenewByBatch(yearStart)
                + statisticsMapper.countOrgRenewByGrantOnly(yearStart);
        resp.setYearNewCount(personalNew + orgNew);
        resp.setYearRenewCount(personalRenew + orgRenew);
        // Q163：当年卡片按「个人会员数 + 企业席位数」拆分（合计由前端相加）
        resp.setYearNewPersonalCount(personalNew);
        resp.setYearNewOrgSeatCount(statisticsMapper.sumNewOrgSeatsByYear(yearStart));
        resp.setYearRenewPersonalCount(personalRenew);
        resp.setYearRenewOrgSeatCount(statisticsMapper.sumRenewOrgSeatsByYear(yearStart));
        // 新获得与延续按客户互斥（MIN(create_time) 只落一边），合计直接相加即去重
        resp.setYearTotalCount(personalNew + personalRenew + orgNew + orgRenew);
        return resp;
    }

    // ==================== 明细 ====================

    @Override
    public PageResult<LicenseCustomerRespVO> getCustomerPage(LicenseCustomerPageReqVO reqVO) {
        LocalDateTime now = LocalDateTime.now();
        int pageNo = reqVO.getPageNo() == null || reqVO.getPageNo() < 1 ? 1 : reqVO.getPageNo();
        int pageSize = reqVO.getPageSize() == null || reqVO.getPageSize() < 1 ? 10 : reqVO.getPageSize();
        String keyword = StrUtil.isBlank(reqVO.getKeyword()) ? null : reqVO.getKeyword().trim();

        long total = statisticsMapper.countCustomerPage(
                reqVO.getTargetType(), reqVO.getStatus(), keyword, reqVO.getRightsType(), now);
        if (total == 0) {
            return PageResult.empty();
        }
        List<LicenseCustomerIdRow> ids = statisticsMapper.selectCustomerPage(
                reqVO.getTargetType(), reqVO.getStatus(), keyword, reqVO.getRightsType(), now,
                (pageNo - 1) * pageSize, pageSize);
        if (CollUtil.isEmpty(ids)) {
            return new PageResult<>(Collections.emptyList(), total);
        }

        // 只装配这一页：批量取会员、企业、订阅、批次、席位
        Set<Long> memberIds = ids.stream()
                .filter(row -> Objects.equals(row.getTargetType(), MemberLicenseGrantDO.TARGET_TYPE_MEMBER))
                .map(LicenseCustomerIdRow::getTargetId).collect(Collectors.toSet());
        Set<Long> orgIds = ids.stream()
                .filter(row -> Objects.equals(row.getTargetType(), MemberLicenseGrantDO.TARGET_TYPE_ORG))
                .map(LicenseCustomerIdRow::getTargetId).collect(Collectors.toSet());

        Map<Long, MemberUserDO> userMap = memberIds.isEmpty() ? Map.of()
                : memberUserMapper.selectBatchIds(memberIds).stream()
                .collect(Collectors.toMap(MemberUserDO::getId, u -> u, (a, b) -> a));
        Map<Long, MaxLabelOrgDO> orgMap = orgIds.isEmpty() ? Map.of()
                : orgMapper.selectBatchIds(orgIds).stream()
                .collect(Collectors.toMap(MaxLabelOrgDO::getId, o -> o, (a, b) -> a));
        Map<Long, List<MemberSubscriptionDO>> subByMember = memberIds.isEmpty() ? Map.of()
                : subscriptionMapper.selectListByMemberIds(memberIds).stream()
                .collect(Collectors.groupingBy(MemberSubscriptionDO::getMemberId));
        Map<Long, List<MaxLabelOrgLicenseDO>> licenseByOrg = orgIds.isEmpty() ? Map.of()
                : licenseMapper.selectList(new LambdaQueryWrapperX<MaxLabelOrgLicenseDO>()
                        .in(MaxLabelOrgLicenseDO::getOrgId, orgIds))
                .stream().collect(Collectors.groupingBy(MaxLabelOrgLicenseDO::getOrgId));
        Map<Long, List<MaxLabelOrgSeatDO>> seatByMember = memberIds.isEmpty() ? Map.of()
                : seatMapper.selectList(new LambdaQueryWrapperX<MaxLabelOrgSeatDO>()
                        .in(MaxLabelOrgSeatDO::getMemberId, memberIds)
                        .eq(MaxLabelOrgSeatDO::getStatus, 0))
                .stream().collect(Collectors.groupingBy(MaxLabelOrgSeatDO::getMemberId));

        List<LicenseCustomerRespVO> list = new ArrayList<>(ids.size());
        for (LicenseCustomerIdRow idRow : ids) {
            if (Objects.equals(idRow.getTargetType(), MemberLicenseGrantDO.TARGET_TYPE_MEMBER)) {
                MemberUserDO user = userMap.get(idRow.getTargetId());
                if (user != null) {
                    list.add(buildMemberRow(user, subByMember, seatByMember, orgMap, licenseByOrg, now));
                }
            } else {
                MaxLabelOrgDO org = orgMap.get(idRow.getTargetId());
                if (org != null) {
                    list.add(buildOrgRow(org, licenseByOrg.getOrDefault(org.getId(), List.of()), now));
                }
            }
        }
        return new PageResult<>(list, total);
    }

    // ==================== 赠送对象（全部个人与企业） ====================

    /**
     * 赠送对象分页（Q162）
     *
     * 与客户明细的区别只有一个：**不要求已有授权** —— 今天要能送给从未购买过的客户，
     * 否则「补偿新客户」仍然无路可走。额度算法与赠送服务共用 {@link LicenseGiftQuota}。
     */
    @Override
    public PageResult<LicenseGiftTargetRespVO> getGiftTargetPage(LicenseGiftTargetPageReqVO reqVO) {
        int pageNo = reqVO.getPageNo() == null || reqVO.getPageNo() < 1 ? 1 : reqVO.getPageNo();
        int pageSize = reqVO.getPageSize() == null || reqVO.getPageSize() < 1 ? 10 : reqVO.getPageSize();
        String keyword = StrUtil.isBlank(reqVO.getKeyword()) ? null : reqVO.getKeyword().trim();

        long total = statisticsMapper.countGiftTargetPage(reqVO.getTargetType(), keyword);
        if (total == 0) {
            return PageResult.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        List<LicenseGiftTargetRow> rows = statisticsMapper.selectGiftTargetPage(
                reqVO.getTargetType(), keyword, now, (pageNo - 1) * pageSize, pageSize);
        LocalDate today = now.toLocalDate();
        List<LicenseGiftTargetRespVO> list = new ArrayList<>(rows.size());
        for (LicenseGiftTargetRow row : rows) {
            LicenseGiftTargetRespVO vo = new LicenseGiftTargetRespVO();
            vo.setTargetType(row.getTargetType());
            vo.setTargetId(row.getTargetId());
            vo.setTargetName(row.getTargetName());
            vo.setTargetMobile(row.getTargetMobile());
            vo.setStatus(row.getStatus());
            LocalDateTime end = row.getEndTime();
            LocalDate lastValidDay = end == null ? null : end.toLocalDate().minusDays(1);
            int remaining = lastValidDay == null ? 0
                    : (int) Math.max(0, ChronoUnit.DAYS.between(today, lastValidDay));
            vo.setEndTime(end == null ? null : end.minusDays(1));
            vo.setRemainingDays(remaining);
            vo.setActive(lastValidDay != null && !lastValidDay.isBefore(today));
            vo.setMaxGiftMonths(LicenseGiftQuota.maxGiftMonths(remaining));
            list.add(vo);
        }
        return new PageResult<>(list, total);
    }

    // ==================== 装配 ====================

    private LicenseCustomerRespVO buildMemberRow(MemberUserDO user,
                                                 Map<Long, List<MemberSubscriptionDO>> subByMember,
                                                 Map<Long, List<MaxLabelOrgSeatDO>> seatByMember,
                                                 Map<Long, MaxLabelOrgDO> orgMap,
                                                 Map<Long, List<MaxLabelOrgLicenseDO>> licenseByOrg,
                                                 LocalDateTime now) {
        LicenseCustomerRespVO row = new LicenseCustomerRespVO();
        row.setTargetType(MemberLicenseGrantDO.TARGET_TYPE_MEMBER);
        row.setTargetId(user.getId());
        row.setTargetName(user.getNickname());
        row.setTargetMobile(user.getMobile());

        List<MemberSubscriptionDO> subs = subByMember.getOrDefault(user.getId(), List.of());
        LocalDateTime personalEnd = subs.stream().map(MemberSubscriptionDO::getEndTime)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        row.setPersonalEndTime(personalEnd);
        row.setStartTime(subs.stream().map(MemberSubscriptionDO::getStartTime)
                .filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null));

        List<String> sources = new ArrayList<>();
        if (subs.stream().anyMatch(r -> r.getSourceOrderItemId() != null)) {
            sources.add(SOURCE_ORDER);
        }
        if (subs.stream().anyMatch(r -> r.getLicenseGrantId() != null)) {
            sources.add(SOURCE_GRANT);
        }

        // 企业席位来源
        List<MaxLabelOrgSeatDO> seats = seatByMember.getOrDefault(user.getId(), List.of());
        if (CollUtil.isNotEmpty(seats)) {
            MaxLabelOrgSeatDO seat = seats.get(0);
            MaxLabelOrgDO org = orgMap.get(seat.getOrgId());
            LocalDateTime orgEnd = licenseByOrg.getOrDefault(seat.getOrgId(), List.of()).stream()
                    .filter(l -> l.getEndTime() != null && l.getEndTime().isAfter(now)
                            && !Boolean.TRUE.equals(l.getRefunded()) && l.getFoldedTime() == null)
                    .map(MaxLabelOrgLicenseDO::getEndTime).max(LocalDateTime::compareTo).orElse(null);
            if (org != null && Objects.equals(org.getStatus(), 0) && orgEnd != null) {
                row.setOrgName(org.getName());
                row.setOrgEndTime(orgEnd);
                sources.add(SOURCE_ORG_SEAT);
            }
        }
        row.setSources(sources);

        LocalDateTime merged = personalEnd;
        if (row.getOrgEndTime() != null) {
            merged = merged == null || row.getOrgEndTime().isAfter(merged) ? row.getOrgEndTime() : merged;
        }
        row.setEndTime(merged);
        fillDays(row, now);
        return row;
    }

    private LicenseCustomerRespVO buildOrgRow(MaxLabelOrgDO org, List<MaxLabelOrgLicenseDO> licenses,
                                              LocalDateTime now) {
        LicenseCustomerRespVO row = new LicenseCustomerRespVO();
        row.setTargetType(MemberLicenseGrantDO.TARGET_TYPE_ORG);
        row.setTargetId(org.getId());
        row.setTargetName(org.getName());
        row.setSources(List.of(licenses.stream().anyMatch(l -> l.getLicenseGrantId() != null)
                ? SOURCE_GRANT : SOURCE_ORDER));
        LocalDateTime orgEnd = licenses.stream().map(MaxLabelOrgLicenseDO::getEndTime)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        row.setOrgEndTime(orgEnd);
        row.setEndTime(orgEnd);
        row.setStartTime(licenses.stream().map(MaxLabelOrgLicenseDO::getStartTime)
                .filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null));
        fillDays(row, now);
        return row;
    }

    /** 剩余/已过期天数与可赠送额度；展示的到期时间统一按"最后有效日"（−1 天） */
    private void fillDays(LicenseCustomerRespVO row, LocalDateTime now) {
        LocalDateTime end = row.getEndTime();
        LocalDate lastValidDay = end == null ? null : end.toLocalDate().minusDays(1);
        row.setEndTime(end == null ? null : end.minusDays(1));
        row.setPersonalEndTime(row.getPersonalEndTime() == null ? null : row.getPersonalEndTime().minusDays(1));
        row.setOrgEndTime(row.getOrgEndTime() == null ? null : row.getOrgEndTime().minusDays(1));

        LocalDate today = now.toLocalDate();
        boolean active = lastValidDay != null && !lastValidDay.isBefore(today);
        row.setActive(active);
        int remaining = lastValidDay == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(today, lastValidDay));
        int expired = lastValidDay == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(lastValidDay, today));
        row.setRemainingDays(remaining);
        row.setExpiredDays(expired);
        row.setMaxGiftMonths(LicenseGiftQuota.maxGiftMonths(remaining));
    }

}
