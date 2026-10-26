package cn.iocoder.yudao.module.member.service.subscription;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.api.subscription.dto.MemberSubscriptionGrantReqDTO;
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
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MaxLabel 会员订阅发放的核心边界：首购、续费顺延、过期后续费、幂等、到期日边界。
 */
@Import(MemberSubscriptionServiceImpl.class)
public class MemberSubscriptionServiceImplTest extends BaseDbUnitTest {

    @Resource
    private MemberSubscriptionServiceImpl subscriptionService;
    @Resource
    private MemberSubscriptionMapper subscriptionMapper;
    @Resource
    private MemberUserMapper userMapper;
    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MaxLabelOrgSeatMapper orgSeatMapper;
    @Resource
    private MaxLabelOrgLicenseMapper orgLicenseMapper;

    private Long memberId;
    private Long orderItemId = 9001L;

    @BeforeEach
    void setUp() {
        MemberUserDO user = MemberUserDO.builder()
                .mobile("13800138000")
                .nickname("测试会员")
                .status(0)
                .registerIp("127.0.0.1")
                .build();
        userMapper.insert(user);
        memberId = user.getId();
        orderItemId = 9001L;
    }

    private MemberSubscriptionGrantReqDTO request(int months, int count, Long itemId) {
        MemberSubscriptionGrantReqDTO req = new MemberSubscriptionGrantReqDTO();
        req.setMemberId(memberId);
        req.setOrderId(8001L);
        req.setOrderItemId(itemId);
        req.setOrderNo("o8001");
        req.setPlanId(11L);
        req.setSpuId(300L);
        req.setSkuId(301L);
        req.setPeriodMonths(months);
        req.setCount(count);
        return req;
    }

    @Test
    void grantFirstPurchaseStartsTodayAndEndsAfterNaturalMonth() {
        MemberSubscriptionDO row = subscriptionService.grant(request(1, 1, orderItemId));
        LocalDate today = LocalDate.now();
        assertEquals(today.atStartOfDay(), row.getStartTime());
        assertEquals(today.plusMonths(1).plusDays(1).atStartOfDay(), row.getEndTime());
        assertEquals(1, row.getPeriodMonths());
        assertFalse(row.getRefunded());
    }

    @Test
    void renewalStartsAtPreviousEndDateWithoutAddingAnotherDay() {
        MemberSubscriptionDO first = subscriptionService.grant(request(1, 1, orderItemId));
        MemberSubscriptionDO second = subscriptionService.grant(request(12, 1, 9002L));
        // end_time 是"最后有效日 + 1 天"；从原到期日（前一天）接续，避免每次续费多 1 天
        LocalDate originalEndDate = first.getEndTime().toLocalDate().minusDays(1);
        assertEquals(originalEndDate, second.getStartTime().toLocalDate());
        assertEquals(originalEndDate.plusMonths(12).plusDays(1).atStartOfDay(),
                second.getEndTime());
    }

    @Test
    void threeConsecutiveRenewalsHaveZeroDayDrift() {
        subscriptionService.grant(request(12, 1, 9001L));
        subscriptionService.grant(request(12, 1, 9002L));
        MemberSubscriptionDO third = subscriptionService.grant(request(12, 1, 9003L));

        assertEquals(LocalDate.now().plusMonths(36),
                third.getEndTime().toLocalDate().minusDays(1));
    }

    @Test
    void expiredSubscriptionStartsFromToday() {
        MemberSubscriptionDO first = subscriptionService.grant(request(1, 1, orderItemId));
        // 把上一段改成已过期：最后有效日是昨天，排他 end_time 是今天 00:00
        first.setEndTime(LocalDate.now().atStartOfDay());
        subscriptionMapper.updateById(first);

        MemberSubscriptionDO second = subscriptionService.grant(request(1, 1, 9002L));
        assertEquals(LocalDate.now().atStartOfDay(), second.getStartTime());
    }

    @Test
    void duplicateOrderItemIsIdempotent() {
        MemberSubscriptionDO first = subscriptionService.grant(request(1, 1, orderItemId));
        MemberSubscriptionDO again = subscriptionService.grant(request(1, 1, orderItemId));
        assertEquals(first.getId(), again.getId());
        List<MemberSubscriptionDO> rows = subscriptionMapper.selectListByMemberId(memberId);
        assertEquals(1, rows.size());
    }

    @Test
    void countMultipliesPeriodAndCurrentSubscriptionShowsLastDay() {
        subscriptionService.grant(request(12, 2, orderItemId));
        MemberSubscriptionDO row = subscriptionMapper.selectListByMemberId(memberId).get(0);
        assertEquals(24, row.getPeriodMonths());
        assertTrue(row.getEndTime().isAfter(LocalDateTime.now()));
        assertEquals(LocalDate.now().plusMonths(24),
                row.getEndTime().toLocalDate().minusDays(1));
    }

    @Test
    void latestByMemberIdsPicksLatestEndTime() {
        subscriptionService.grant(request(1, 1, 9001L));
        MemberSubscriptionDO second = subscriptionService.grant(request(1, 1, 9002L));

        MemberSubscriptionDO latest = subscriptionService
                .getLatestByMemberIds(List.of(memberId)).get(memberId);
        assertNotNull(latest);
        assertEquals(second.getId(), latest.getId());
    }

    @Test
    void currentSubscriptionExpiresAtExclusiveEndTime() {
        MemberSubscriptionDO row = subscriptionService.grant(request(1, 1, orderItemId));
        row.setEndTime(LocalDate.now().atStartOfDay());
        subscriptionMapper.updateById(row);

        var current = subscriptionService.getCurrent(memberId);
        assertFalse(current.getActive());
        assertEquals(LocalDate.now().minusDays(1), current.getEndDate());
        assertEquals(0L, current.getRemainingDays());
    }

    @Test
    void currentSubscriptionIsActiveOnExpiryDay() {
        subscriptionService.grant(request(1, 1, orderItemId));
        var current = subscriptionService.getCurrent(memberId);
        assertTrue(current.getActive());
        assertEquals(LocalDate.now(), current.getStartDate());
        assertEquals(LocalDate.now().plusMonths(1), current.getEndDate());
    }

    // ==================== 企业席位来源 ====================

    private Long createEnterpriseSource(int months, boolean seatActive, boolean expired) {
        MaxLabelOrgDO org = MaxLabelOrgDO.builder()
                .name("测试企业").ownerMemberId(memberId).status(0).remark("").build();
        orgMapper.insert(org);
        orgSeatMapper.insert(MaxLabelOrgSeatDO.builder()
                .orgId(org.getId()).memberId(memberId)
                .status(seatActive ? 0 : 1).operatorType(1).operatorId(memberId)
                .assignedTime(LocalDateTime.now()).build());
        LocalDate today = LocalDate.now();
        LocalDateTime start = expired ? today.minusMonths(2).atStartOfDay() : today.atStartOfDay();
        LocalDateTime end = expired ? today.minusDays(1).atStartOfDay()
                : today.plusMonths(months).plusDays(1).atStartOfDay();
        orgLicenseMapper.insert(MaxLabelOrgLicenseDO.builder()
                .orgId(org.getId()).planId(1L).spuId(1L).skuId(1L)
                .periodMonths(months).seatsTotal(1)
                .startTime(start).endTime(end)
                .sourceOrderId(8001L).sourceOrderItemId(9001L).sourceOrderNo("o8001")
                .refunded(false).build());
        return org.getId();
    }

    @Test
    void refundedEnterpriseLicenseDoesNotExtendEndDate() {
        Long orgId = createEnterpriseSource(12, true, false);
        // 已退款的更长批次：不能把成员权益延长到 24 个月
        orgLicenseMapper.insert(MaxLabelOrgLicenseDO.builder()
                .orgId(orgId).planId(1L).spuId(1L).skuId(1L)
                .periodMonths(24).seatsTotal(1)
                .startTime(LocalDate.now().atStartOfDay())
                .endTime(LocalDate.now().plusMonths(24).plusDays(1).atStartOfDay())
                .sourceOrderId(8002L).sourceOrderItemId(9002L).sourceOrderNo("o9002")
                .refunded(true).build());

        var current = subscriptionService.getCurrent(memberId);
        assertEquals(LocalDate.now().plusMonths(12), current.getEndDate());
    }

    @Test
    void enterpriseSeatIsEffectiveWithoutPersonal() {
        createEnterpriseSource(12, true, false);
        var current = subscriptionService.getCurrent(memberId);
        assertTrue(current.getActive());
        assertEquals(2, current.getSourceType());
        assertEquals("测试企业", current.getOrgName());
        assertEquals(LocalDate.now().plusMonths(12), current.getEndDate());
    }

    @Test
    void enterpriseWinsWhenLaterThanPersonal() {
        subscriptionService.grant(request(1, 1, 9001L));
        createEnterpriseSource(12, true, false);
        var current = subscriptionService.getCurrent(memberId);
        assertEquals(2, current.getSourceType());
        assertEquals(LocalDate.now().plusMonths(12), current.getEndDate());
    }

    @Test
    void personalWinsWhenLaterButOrgNameStillReturned() {
        subscriptionService.grant(request(24, 1, 9001L));
        createEnterpriseSource(1, true, false);
        var current = subscriptionService.getCurrent(memberId);
        assertEquals(1, current.getSourceType());
        assertEquals(LocalDate.now().plusMonths(24), current.getEndDate());
        assertEquals("测试企业", current.getOrgName());
    }

    @Test
    void revokedSeatFallsBackToPersonal() {
        subscriptionService.grant(request(1, 1, 9001L));
        createEnterpriseSource(12, false, false);
        var current = subscriptionService.getCurrent(memberId);
        assertEquals(1, current.getSourceType());
        assertEquals(LocalDate.now().plusMonths(1), current.getEndDate());
    }

    @Test
    void expiredEnterpriseLicenseIsInactive() {
        createEnterpriseSource(1, true, true);
        var current = subscriptionService.getCurrent(memberId);
        assertFalse(current.getActive());
        assertEquals(1, current.getSourceType());
    }

}
