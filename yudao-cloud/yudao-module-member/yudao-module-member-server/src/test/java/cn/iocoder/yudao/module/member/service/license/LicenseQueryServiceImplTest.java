package cn.iocoder.yudao.module.member.service.license;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseCustomerPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseCustomerRespVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGiftTargetPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGiftTargetRespVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseSummaryRespVO;
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
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 授权看板统计口径（020 / US67 / US68）
 *
 * 钉住四件事：
 * · 个人有效 = 未过期订阅的 distinct 会员；只有企业席位的会员**不计入个人**
 * · 企业有效 = 启用 + 有有效批次，**不看席位是否分配**（Q156）
 * · 当年「新获得 / 延续 / 合计」用 create_time 判定，合计按客户去重（Q155）
 * · 明细与总览**同源同口径**：有效明细条数 == 个人有效 + 企业有效（FR-547 / SC-204）
 */
@Import({LicenseQueryServiceImpl.class})
public class LicenseQueryServiceImplTest extends BaseDbUnitTest {

    @Resource
    private LicenseQueryService licenseQueryService;
    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MemberSubscriptionMapper subscriptionMapper;
    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MaxLabelOrgLicenseMapper licenseMapper;
    @Resource
    private MaxLabelOrgSeatMapper seatMapper;

    // ==================== 造数 ====================

    private MemberUserDO createUser(String nickname, String mobile) {
        MemberUserDO user = MemberUserDO.builder()
                .nickname(nickname).mobile(mobile).status(0)
                .avatar("").registerIp("127.0.0.1").build();
        memberUserMapper.insert(user);
        return user;
    }

    private MemberSubscriptionDO createSubscription(Long memberId, LocalDateTime endTime) {
        return createSubscription(memberId, endTime, LocalDateTime.now());
    }

    private MemberSubscriptionDO createSubscription(Long memberId, LocalDateTime endTime, LocalDateTime createTime) {
        MemberSubscriptionDO row = MemberSubscriptionDO.builder()
                .memberId(memberId).edition("professional").periodMonths(12)
                .startTime(endTime.minusMonths(12))
                .endTime(endTime)
                .sourceOrderId(1L).sourceOrderItemId(System.nanoTime())
                .sourceOrderNo("NO-" + System.nanoTime())
                .refunded(false)
                .build();
        row.setCreateTime(createTime);
        subscriptionMapper.insert(row);
        return row;
    }

    private MaxLabelOrgDO createOrg(String name, int status) {
        MaxLabelOrgDO org = MaxLabelOrgDO.builder().name(name).status(status).ownerMemberId(1L).build();
        orgMapper.insert(org);
        return org;
    }

    private MaxLabelOrgLicenseDO createLicense(Long orgId, int seats, LocalDateTime endTime,
                                               boolean refunded, LocalDateTime foldedTime) {
        return createLicense(orgId, seats, endTime, refunded, foldedTime, null);
    }

    private MaxLabelOrgLicenseDO createLicense(Long orgId, int seats, LocalDateTime endTime,
                                               boolean refunded, LocalDateTime foldedTime,
                                               LocalDateTime createTime) {
        MaxLabelOrgLicenseDO row = MaxLabelOrgLicenseDO.builder()
                .orgId(orgId).edition("enterprise").periodMonths(12).seatsTotal(seats)
                .startTime(LocalDate.now().minusMonths(1).atStartOfDay())
                .endTime(endTime).refunded(refunded).foldedTime(foldedTime)
                .build();
        if (createTime != null) {
            row.setCreateTime(createTime);
        }
        licenseMapper.insert(row);
        return row;
    }

    private MaxLabelOrgSeatDO createSeat(Long orgId, Long memberId) {
        MaxLabelOrgSeatDO seat = MaxLabelOrgSeatDO.builder()
                .orgId(orgId).memberId(memberId).status(0).operatorType(2).operatorId(1L)
                .assignedTime(LocalDateTime.now()).build();
        seatMapper.insert(seat);
        return seat;
    }

    private LocalDateTime inDays(int days) {
        return LocalDateTime.now().plusDays(days);
    }

    // ==================== 总览 ====================

    @Test
    public void testSummary_personalActive_onlyUnexpired() {
        MemberUserDO active = createUser("有效", "13800000001");
        MemberUserDO expired = createUser("过期", "13800000002");
        createSubscription(active.getId(), inDays(30));
        createSubscription(expired.getId(), inDays(-1));

        LicenseSummaryRespVO summary = licenseQueryService.getSummary();

        assertEquals(1, summary.getPersonalActiveCount(), "已过期的订阅不计入个人有效");
    }

    @Test
    public void testSummary_memberWithOnlyOrgSeat_notCountedAsPersonal() {
        MemberUserDO employee = createUser("员工", "13800000003");
        MaxLabelOrgDO org = createOrg("某企业", 0);
        createLicense(org.getId(), 10, inDays(100), false, null);
        createSeat(org.getId(), employee.getId());

        LicenseSummaryRespVO summary = licenseQueryService.getSummary();

        assertEquals(0, summary.getPersonalActiveCount(), "只有企业席位的会员不计入个人有效");
        assertEquals(1, summary.getOrgActiveCount());
        assertEquals(1, summary.getOrgSeatActiveCount(), "已分配 1 个席位");
        assertEquals(10, summary.getOrgSeatTotalCount());
    }

    @Test
    public void testSummary_orgWithoutSeatAssigned_stillCounts() {
        MaxLabelOrgDO org = createOrg("买了没分配", 0);
        createLicense(org.getId(), 20, inDays(100), false, null);

        LicenseSummaryRespVO summary = licenseQueryService.getSummary();

        assertEquals(1, summary.getOrgActiveCount(), "企业有有效批次即算，不看席位是否分配（Q156）");
        assertEquals(0, summary.getOrgSeatActiveCount());
    }

    @Test
    public void testSummary_disabledOrg_refundedAndFoldedBatchesExcluded() {
        MaxLabelOrgDO disabled = createOrg("停用企业", 1);
        createLicense(disabled.getId(), 5, inDays(100), false, null);
        MaxLabelOrgDO refunded = createOrg("退款批次", 0);
        createLicense(refunded.getId(), 5, inDays(100), true, null);
        MaxLabelOrgDO folded = createOrg("已折算", 0);
        createLicense(folded.getId(), 5, inDays(100), false, LocalDateTime.now());

        assertEquals(0, licenseQueryService.getSummary().getOrgActiveCount());
    }

    @Test
    public void testSummary_yearNewAndRenew_usesCreateTime() {
        // 去年就有、今年续费 → 延续
        MemberUserDO renew = createUser("续费", "13800000011");
        createSubscription(renew.getId(), inDays(30), LocalDateTime.now().minusYears(2));
        createSubscription(renew.getId(), inDays(400), LocalDateTime.now());
        // 今年首次 → 新获得
        MemberUserDO fresh = createUser("新客", "13800000012");
        createSubscription(fresh.getId(), inDays(30), LocalDateTime.now());

        LicenseSummaryRespVO summary = licenseQueryService.getSummary();

        assertEquals(1, summary.getYearNewCount());
        assertEquals(1, summary.getYearRenewCount());
        assertEquals(2, summary.getYearTotalCount(), "合计按客户去重");
    }

    @Test
    public void testSummary_yearSplitByPersonalAndOrgSeats() {
        // 个人：去年已有 + 今年续费 → 延续；只有今年一笔 → 新获得
        MemberUserDO renewMember = createUser("续费会员", "13800000131");
        createSubscription(renewMember.getId(), inDays(30), LocalDateTime.now().minusYears(2));
        createSubscription(renewMember.getId(), inDays(400), LocalDateTime.now());
        MemberUserDO newMember = createUser("新会员", "13800000132");
        createSubscription(newMember.getId(), inDays(30), LocalDateTime.now());

        // 企业：首批在本年 → 新获得（今年批次 10 席）；
        //       首批在去年 → 延续（去年 4 席不算，今年新增 6 席）
        MaxLabelOrgDO newOrg = createOrg("新企业", 0);
        createLicense(newOrg.getId(), 10, inDays(300), false, null);
        MaxLabelOrgDO renewOrg = createOrg("老企业", 0);
        createLicense(renewOrg.getId(), 4, inDays(10), false, null, LocalDateTime.now().minusYears(1));
        createLicense(renewOrg.getId(), 6, inDays(300), false, null);

        LicenseSummaryRespVO summary = licenseQueryService.getSummary();

        assertEquals(1, summary.getYearNewPersonalCount());
        assertEquals(1, summary.getYearRenewPersonalCount());
        assertEquals(10, summary.getYearNewOrgSeatCount(), "新获得企业的当年批次席位数");
        assertEquals(6, summary.getYearRenewOrgSeatCount(), "延续企业的当年新增批次席位数");
    }

    @Test
    public void testSummary_yearOrgSeats_excludesRefundedAndFoldedBatches() {
        MaxLabelOrgDO refunded = createOrg("退款企业", 0);
        createLicense(refunded.getId(), 10, inDays(300), true, null);
        MaxLabelOrgDO folded = createOrg("折算企业", 0);
        createLicense(folded.getId(), 10, inDays(300), false, LocalDateTime.now());

        LicenseSummaryRespVO summary = licenseQueryService.getSummary();

        assertEquals(0, summary.getYearNewOrgSeatCount(), "退款/折算批次不构成有效席位");
        assertEquals(0, summary.getYearRenewOrgSeatCount());
    }

    @Test
    public void testSummary_emptyDatabase_returnsZeros() {
        LicenseSummaryRespVO summary = licenseQueryService.getSummary();

        assertEquals(0, summary.getPersonalActiveCount());
        assertEquals(0, summary.getOrgActiveCount());
        assertEquals(0, summary.getYearTotalCount());
        assertEquals(LocalDate.now().getYear(), summary.getYear());
    }

    // ==================== 明细 ====================

    @Test
    public void testCustomerPage_dualSource_mergedEndTakesLater() {
        MemberUserDO member = createUser("双来源", "13800000021");
        createSubscription(member.getId(), inDays(30));
        MaxLabelOrgDO org = createOrg("某企业", 0);
        createLicense(org.getId(), 5, inDays(200), false, null);
        createSeat(org.getId(), member.getId());

        PageResult<LicenseCustomerRespVO> page = licenseQueryService.getCustomerPage(new LicenseCustomerPageReqVO());

        LicenseCustomerRespVO row = page.getList().stream()
                .filter(r -> r.getTargetId().equals(member.getId())).findFirst().orElseThrow();
        assertTrue(row.getSources().containsAll(java.util.List.of("ORDER", "ORG_SEAT")));
        assertNotNull(row.getPersonalEndTime());
        assertNotNull(row.getOrgEndTime());
        assertFalse(row.getEndTime().isBefore(row.getOrgEndTime()), "合并到期取较晚");
    }

    @Test
    public void testCustomerPage_expiredFilter_andSameCountAsSummary() {
        MemberUserDO active = createUser("有效", "13800000031");
        MemberUserDO expired = createUser("过期", "13800000032");
        createSubscription(active.getId(), inDays(10));
        createSubscription(expired.getId(), inDays(-3));
        MaxLabelOrgDO org = createOrg("企业", 0);
        createLicense(org.getId(), 3, inDays(50), false, null);

        LicenseCustomerPageReqVO expiredReq = new LicenseCustomerPageReqVO();
        expiredReq.setStatus(1);
        PageResult<LicenseCustomerRespVO> expiredPage = licenseQueryService.getCustomerPage(expiredReq);
        assertEquals(1, expiredPage.getTotal());
        assertEquals(0, expiredPage.getList().get(0).getRemainingDays());
        assertTrue(expiredPage.getList().get(0).getExpiredDays() > 0);

        LicenseCustomerPageReqVO activeReq = new LicenseCustomerPageReqVO();
        activeReq.setStatus(0);
        LicenseSummaryRespVO summary = licenseQueryService.getSummary();
        assertEquals(summary.getPersonalActiveCount() + summary.getOrgActiveCount(),
                licenseQueryService.getCustomerPage(activeReq).getTotal(),
                "明细与看板必须同源同口径（FR-547 / SC-204）");
    }

    @Test
    public void testCustomerPage_seatOnlyMember_listed_andAccountingHolds() {
        // 2026-11-16 所有者裁定 B：只有企业席位的会员**要**在明细里可见，
        // 看板把「订阅会员」与「席位会员」分开计数，对账按分栏成立。
        MemberUserDO employee = createUser("只有席位", "13800000051");
        MaxLabelOrgDO org = createOrg("某企业", 0);
        createLicense(org.getId(), 10, inDays(200), false, null);
        createSeat(org.getId(), employee.getId());
        MemberUserDO normal = createUser("有订阅", "13800000052");
        createSubscription(normal.getId(), inDays(30));

        LicenseCustomerPageReqVO activeReq = new LicenseCustomerPageReqVO();
        activeReq.setStatus(0);
        LicenseSummaryRespVO summary = licenseQueryService.getSummary();
        PageResult<LicenseCustomerRespVO> page = licenseQueryService.getCustomerPage(activeReq);

        assertEquals(1, summary.getPersonalSeatOnlyCount(), "只有席位的会员单独计数");
        assertEquals(summary.getPersonalActiveCount() + summary.getPersonalSeatOnlyCount()
                        + summary.getOrgActiveCount(), page.getTotal(),
                "明细有效条数 == 订阅会员 + 席位会员 + 企业有效（SC-204 新口径）");
        LicenseCustomerRespVO employeeRow = page.getList().stream()
                .filter(r -> r.getTargetId().equals(employee.getId())).findFirst().orElse(null);
        assertNotNull(employeeRow, "只有席位的会员要能看到");
        assertTrue(employeeRow.getSources().contains("ORG_SEAT"));

        // T092：席位会员下钻只列席位会员
        LicenseCustomerPageReqVO seatOnlyReq = new LicenseCustomerPageReqVO();
        seatOnlyReq.setTargetType(1);
        seatOnlyReq.setRightsType(2);
        PageResult<LicenseCustomerRespVO> seatOnlyPage = licenseQueryService.getCustomerPage(seatOnlyReq);
        assertEquals(1, seatOnlyPage.getTotal());
        assertEquals(employee.getId(), seatOnlyPage.getList().get(0).getTargetId());
    }

    // ==================== 赠送对象（Q162） ====================

    @Test
    public void testGiftTargetPage_includesCustomersWithoutAnyLicense() {
        // 赠送是独立入口：从未购买过的个人和企业也必须能选到（否则"补偿新客户"仍无路可走）
        MemberUserDO fresh = createUser("新会员", "13700000001");
        MaxLabelOrgDO freshOrg = createOrg("新企业", 0);

        PageResult<LicenseGiftTargetRespVO> page =
                licenseQueryService.getGiftTargetPage(new LicenseGiftTargetPageReqVO());

        assertEquals(2, page.getTotal());
        LicenseGiftTargetRespVO memberRow = page.getList().stream()
                .filter(r -> r.getTargetType() == 1 && r.getTargetId().equals(fresh.getId()))
                .findFirst().orElseThrow();
        assertNull(memberRow.getEndTime(), "无授权时到期为空");
        assertFalse(memberRow.getActive());
        assertEquals(12, memberRow.getMaxGiftMonths(), "无授权也能送满 12 个月");
        LicenseGiftTargetRespVO orgRow = page.getList().stream()
                .filter(r -> r.getTargetType() == 2 && r.getTargetId().equals(freshOrg.getId()))
                .findFirst().orElseThrow();
        assertNull(orgRow.getEndTime());
        assertEquals(12, orgRow.getMaxGiftMonths());
    }

    @Test
    public void testGiftTargetPage_maxGiftMonthsMatchesGrantQuota() {
        MemberUserDO member = createUser("有订阅", "13700000011");
        createSubscription(member.getId(), inDays(300));
        MaxLabelOrgDO org = createOrg("有批次", 0);
        createLicense(org.getId(), 5, inDays(100), false, null);

        PageResult<LicenseGiftTargetRespVO> page =
                licenseQueryService.getGiftTargetPage(new LicenseGiftTargetPageReqVO());

        LicenseGiftTargetRespVO memberRow = page.getList().stream()
                .filter(r -> r.getTargetType() == 1 && r.getTargetId().equals(member.getId()))
                .findFirst().orElseThrow();
        assertTrue(memberRow.getActive());
        assertEquals(2, memberRow.getMaxGiftMonths(), "剩 300 天 ≈ 10 个月 → 最多再送 2 个月");

        LicenseGiftTargetRespVO orgRow = page.getList().stream()
                .filter(r -> r.getTargetType() == 2 && r.getTargetId().equals(org.getId()))
                .findFirst().orElseThrow();
        assertTrue(orgRow.getActive());
        assertEquals(8, orgRow.getMaxGiftMonths(), "剩 100 天 ≈ 4 个月 → 最多再送 8 个月");
    }

    @Test
    public void testGiftTargetPage_expiredOrg_endTimeNull_andDisabledOrgStillListed() {
        MaxLabelOrgDO expired = createOrg("已过期企业", 0);
        createLicense(expired.getId(), 5, inDays(-10), false, null);
        MaxLabelOrgDO disabled = createOrg("停用企业", 1);

        PageResult<LicenseGiftTargetRespVO> page =
                licenseQueryService.getGiftTargetPage(new LicenseGiftTargetPageReqVO());

        LicenseGiftTargetRespVO expiredRow = page.getList().stream()
                .filter(r -> r.getTargetId().equals(expired.getId())).findFirst().orElseThrow();
        assertNull(expiredRow.getEndTime(), "过期批次不当作当前有效期");
        assertFalse(expiredRow.getActive());
        assertEquals(12, expiredRow.getMaxGiftMonths());
        LicenseGiftTargetRespVO disabledRow = page.getList().stream()
                .filter(r -> r.getTargetId().equals(disabled.getId())).findFirst().orElseThrow();
        assertEquals(1, disabledRow.getStatus(), "停用企业也要列出来（赠送时由服务端提示先启用）");
    }

    @Test
    public void testGiftTargetPage_keywordAndTargetTypeFilter() {
        createUser("张三", "13700000021");
        createOrg("南京路桥机械", 0);

        LicenseGiftTargetPageReqVO byKeyword = new LicenseGiftTargetPageReqVO();
        byKeyword.setKeyword("南京");
        assertEquals(1, licenseQueryService.getGiftTargetPage(byKeyword).getTotal());

        LicenseGiftTargetPageReqVO byMobile = new LicenseGiftTargetPageReqVO();
        byMobile.setKeyword("13700000021");
        assertEquals(1, licenseQueryService.getGiftTargetPage(byMobile).getTotal());

        LicenseGiftTargetPageReqVO membersOnly = new LicenseGiftTargetPageReqVO();
        membersOnly.setTargetType(1);
        PageResult<LicenseGiftTargetRespVO> memberPage = licenseQueryService.getGiftTargetPage(membersOnly);
        assertEquals(1, memberPage.getTotal());
        assertEquals(1, memberPage.getList().get(0).getTargetType());
    }

    @Test
    public void testCustomerPage_keywordMatchesNicknameMobileOrgName() {
        MemberUserDO member = createUser("张三", "13900000041");
        createSubscription(member.getId(), inDays(10));
        MaxLabelOrgDO org = createOrg("南京路桥机械", 0);
        createLicense(org.getId(), 3, inDays(50), false, null);

        LicenseCustomerPageReqVO byMobile = new LicenseCustomerPageReqVO();
        byMobile.setKeyword("13900000041");
        assertEquals(1, licenseQueryService.getCustomerPage(byMobile).getTotal());

        LicenseCustomerPageReqVO byOrg = new LicenseCustomerPageReqVO();
        byOrg.setKeyword("南京路桥");
        assertEquals(1, licenseQueryService.getCustomerPage(byOrg).getTotal());
    }

}
