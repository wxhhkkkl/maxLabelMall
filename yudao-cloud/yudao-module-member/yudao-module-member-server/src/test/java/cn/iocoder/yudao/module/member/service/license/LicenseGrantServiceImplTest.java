package cn.iocoder.yudao.module.member.service.license;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.license.MemberLicenseGrantDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.license.MemberLicenseGrantMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgLicenseMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMapper;
import cn.iocoder.yudao.module.member.dal.mysql.subscription.MemberSubscriptionMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 授权赠送（020 / US69）
 *
 * 钉住的都是会赔钱或赔信任的点：顺延口径、上限、企业无批次、停用企业、理由必填、失败不留痕。
 */
@Import({LicenseGrantServiceImpl.class, cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionServiceImpl.class})
public class LicenseGrantServiceImplTest extends BaseDbUnitTest {

    @Resource
    private LicenseGrantService licenseGrantService;
    @Resource
    private cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService subscriptionService;
    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MemberSubscriptionMapper subscriptionMapper;
    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MaxLabelOrgLicenseMapper licenseMapper;
    @Resource
    private MemberLicenseGrantMapper grantMapper;

    private MemberUserDO createUser() {
        MemberUserDO user = MemberUserDO.builder()
                .nickname("测试会员").mobile("13800009999").status(0)
                .avatar("").registerIp("127.0.0.1").build();
        memberUserMapper.insert(user);
        return user;
    }

    private MaxLabelOrgDO createOrg(int status) {
        MaxLabelOrgDO org = MaxLabelOrgDO.builder().name("测试企业" + System.nanoTime()).status(status).ownerMemberId(1L).build();
        orgMapper.insert(org);
        return org;
    }

    private MemberSubscriptionDO createSubscription(Long memberId, LocalDateTime endTime) {
        MemberSubscriptionDO row = MemberSubscriptionDO.builder()
                .memberId(memberId).edition("standard").periodMonths(1)
                .startTime(endTime.minusMonths(1)).endTime(endTime)
                .sourceOrderId(1L).sourceOrderItemId(System.nanoTime())
                .sourceOrderNo("NO-1").refunded(false).build();
        subscriptionMapper.insert(row);
        return row;
    }

    private MaxLabelOrgLicenseDO createLicense(Long orgId, int seats, LocalDateTime endTime) {
        MaxLabelOrgLicenseDO row = MaxLabelOrgLicenseDO.builder()
                .orgId(orgId).edition("enterprise").periodMonths(12).seatsTotal(seats)
                .startTime(LocalDate.now().minusMonths(1).atStartOfDay())
                .endTime(endTime).refunded(false).build();
        licenseMapper.insert(row);
        return row;
    }

    private LicenseGrantReqVO memberReq(Long memberId, int months, int contentType) {
        LicenseGrantReqVO reqVO = new LicenseGrantReqVO();
        reqVO.setTargetType(MemberLicenseGrantDO.TARGET_TYPE_MEMBER);
        reqVO.setTargetId(memberId);
        reqVO.setContentType(contentType);
        reqVO.setMonths(months);
        reqVO.setReason("测试补偿");
        return reqVO;
    }

    private LicenseGrantReqVO orgReq(Long orgId, int contentType, Integer months, Integer seats) {
        LicenseGrantReqVO reqVO = new LicenseGrantReqVO();
        reqVO.setTargetType(MemberLicenseGrantDO.TARGET_TYPE_ORG);
        reqVO.setTargetId(orgId);
        reqVO.setContentType(contentType);
        reqVO.setMonths(months);
        reqVO.setSeats(seats);
        reqVO.setReason("企业补偿");
        return reqVO;
    }

    // ==================== 个人 ====================

    @Test
    public void testGrantMember_extendsFromCurrentEnd() {
        MemberUserDO user = createUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentEnd = now.toLocalDate().plusMonths(2).atStartOfDay(); // 还剩约 2 个月
        createSubscription(user.getId(), currentEnd);

        LicenseGrantRespVO resp = licenseGrantService.grant(memberReq(user.getId(), 1, 1), 100L);

        MemberSubscriptionDO gift = subscriptionMapper.selectList(
                        new LambdaQueryWrapperX<MemberSubscriptionDO>()
                                .eq(MemberSubscriptionDO::getMemberId, user.getId()))
                .stream().filter(r -> r.getLicenseGrantId() != null).findFirst().orElseThrow();
        assertEquals("professional", gift.getEdition(), "个人赠送固定专业版（Q158）");
        assertNull(gift.getSourceOrderItemId(), "赠送行没有订单");
        assertEquals(1, gift.getPeriodMonths());
        assertEquals(currentEnd.toLocalDate().plusMonths(1).atStartOfDay(), gift.getEndTime(),
                "从原到期日顺延，不覆盖剩余时长");
        assertEquals(gift.getEndTime().minusDays(1), resp.getAfterEndTime());
    }

    @Test
    public void testGrantMember_expired_startsFromToday() {
        MemberUserDO user = createUser();
        createSubscription(user.getId(), LocalDateTime.now().minusDays(10));

        licenseGrantService.grant(memberReq(user.getId(), 1, 1), 100L);

        MemberSubscriptionDO gift = subscriptionMapper.selectList(
                        new LambdaQueryWrapperX<MemberSubscriptionDO>()
                                .eq(MemberSubscriptionDO::getMemberId, user.getId()))
                .stream().filter(r -> r.getLicenseGrantId() != null).findFirst().orElseThrow();
        assertEquals(LocalDate.now().atStartOfDay(), gift.getStartTime(), "已过期从今天起算");
    }

    @Test
    public void testGrantMember_limitExceeded() {
        MemberUserDO user = createUser();
        // 还剩约 10 个月 → 最多送 2 个月
        createSubscription(user.getId(), LocalDate.now().plusDays(300).atStartOfDay());

        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.grant(memberReq(user.getId(), 3, 1), 100L));
        assertEquals(LICENSE_GIFT_LIMIT_EXCEEDED.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("2"), "要告诉运营最多还能送几个月，实际：" + e.getMessage());
        assertTrue(grantMapper.selectList(new LambdaQueryWrapperX<>()).isEmpty(), "被拒时不留记录");
    }

    @Test
    public void testGrantMember_invalidTier() {
        MemberUserDO user = createUser();
        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.grant(memberReq(user.getId(), 5, 1), 100L));
        assertEquals(LICENSE_GIFT_MONTHS_INVALID.getCode(), e.getCode());
    }

    @Test
    public void testGrantMember_seatsNotAllowed() {
        MemberUserDO user = createUser();
        LicenseGrantReqVO reqVO = memberReq(user.getId(), 1, 2);
        reqVO.setSeats(1);
        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.grant(reqVO, 100L));
        assertEquals(LICENSE_GIFT_CONTENT_INVALID.getCode(), e.getCode());
    }

    @Test
    public void testGrant_reasonRequired() {
        MemberUserDO user = createUser();
        LicenseGrantReqVO reqVO = memberReq(user.getId(), 1, 1);
        reqVO.setReason("   ");
        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.grant(reqVO, 100L));
        assertEquals(LICENSE_GIFT_REASON_REQUIRED.getCode(), e.getCode());
    }

    // ==================== 企业 ====================

    @Test
    public void testGrantOrgSeats_addsToActiveBatch_keepsEndTime() {
        MaxLabelOrgDO org = createOrg(0);
        LocalDateTime end = LocalDateTime.now().plusDays(200).withHour(0).withMinute(0).withSecond(0).withNano(0);
        createLicense(org.getId(), 20, end);

        LicenseGrantRespVO resp = licenseGrantService.grant(orgReq(org.getId(), 2, null, 5), 100L);

        MaxLabelOrgLicenseDO batch = licenseMapper.selectList(
                        new LambdaQueryWrapperX<MaxLabelOrgLicenseDO>()
                                .eq(MaxLabelOrgLicenseDO::getOrgId, org.getId()))
                .get(0);
        assertEquals(25, batch.getSeatsTotal(), "席位加到当前批次");
        assertEquals(end, batch.getEndTime(), "到期日不变");
        assertEquals(25, resp.getAfterSeats());
    }

    @Test
    public void testGrantOrgTime_extendsEndAndPeriodMonths() {
        MaxLabelOrgDO org = createOrg(0);
        LocalDateTime end = LocalDateTime.now().plusDays(100).withHour(0).withMinute(0).withSecond(0).withNano(0);
        MaxLabelOrgLicenseDO batch = createLicense(org.getId(), 10, end);

        licenseGrantService.grant(orgReq(org.getId(), 1, 3, null), 100L);

        MaxLabelOrgLicenseDO updated = licenseMapper.selectById(batch.getId());
        assertEquals(end.toLocalDate().plusMonths(3).atStartOfDay(), updated.getEndTime());
        assertEquals(15, updated.getPeriodMonths(), "period_months 同步 +3（否则展示自相矛盾）");
        assertEquals(10, updated.getSeatsTotal(), "席位数不变");
    }

    @Test
    public void testGrantOrg_noBatch_seatsRequireMonths() {
        MaxLabelOrgDO org = createOrg(0);
        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.grant(orgReq(org.getId(), 2, null, 5), 100L));
        assertEquals(LICENSE_GIFT_MONTHS_REQUIRED.getCode(), e.getCode());
    }

    @Test
    public void testGrantOrg_noBatch_createsGiftBatch() {
        MaxLabelOrgDO org = createOrg(0);

        LicenseGrantRespVO resp = licenseGrantService.grant(orgReq(org.getId(), 2, 3, 5), 100L);

        List<MaxLabelOrgLicenseDO> batches = licenseMapper.selectList(
                new LambdaQueryWrapperX<MaxLabelOrgLicenseDO>()
                        .eq(MaxLabelOrgLicenseDO::getOrgId, org.getId()));
        assertEquals(1, batches.size());
        MaxLabelOrgLicenseDO created = batches.get(0);
        assertEquals(5, created.getSeatsTotal());
        assertNotNull(created.getLicenseGrantId(), "赠送批次要指回赠送记录");
        assertNull(created.getSourceOrderItemId());
        assertEquals(5, resp.getAfterSeats());
    }

    @Test
    public void testGrantOrg_disabled() {
        MaxLabelOrgDO org = createOrg(1);
        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.grant(orgReq(org.getId(), 1, 1, null), 100L));
        assertEquals(LICENSE_ORG_DISABLED.getCode(), e.getCode());
    }

    @Test
    public void testGrantMember_currentReflectsNewEndImmediately() {
        // FR-550：赠送后客户侧"下一次读取"就是新到期日，不需要发版/重新登录
        MemberUserDO user = createUser();
        LocalDateTime currentEnd = LocalDate.now().plusMonths(1).atStartOfDay();
        createSubscription(user.getId(), currentEnd);

        licenseGrantService.grant(memberReq(user.getId(), 1, 1), 100L);

        var current = subscriptionService.getCurrent(user.getId());
        assertTrue(current.getActive());
        // 起点是「原到期日 − 1 天」（最后有效日），顺延一个月后展示时再 −1 天
        assertEquals(currentEnd.toLocalDate().minusDays(1).plusMonths(1), current.getEndDate());
    }

    // ==================== 留痕 ====================

    @Test
    public void testGrant_recordContainsBeforeAfterAndReason() {
        MaxLabelOrgDO org = createOrg(0);
        LocalDateTime end = LocalDateTime.now().plusDays(150).withHour(0).withMinute(0).withSecond(0).withNano(0);
        createLicense(org.getId(), 3, end);

        licenseGrantService.grant(orgReq(org.getId(), 2, null, 2), 100L);

        MemberLicenseGrantDO grant = grantMapper.selectList(new LambdaQueryWrapperX<>()).get(0);
        assertEquals("企业补偿", grant.getReason());
        assertEquals(3, grant.getBeforeSeats());
        assertEquals(5, grant.getAfterSeats());
        assertEquals(100L, grant.getOperatorId());
        assertEquals(end, grant.getBeforeEndTime());
    }

}
