package cn.iocoder.yudao.module.member.service.license;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGrantRevokeReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.license.MemberLicenseGrantDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.license.MemberLicenseGrantMapper;
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
import java.util.Comparator;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 赠送撤回（020 / Q164，2026-11-19）
 *
 * 钉住的都是"撤错会赔权益"的点：只看最近一笔、个人删行即还原、席位数已分配不许撤、
 * 送时长要把到期日与 period_months 一起退回去、赠送记录本身不删。
 */
@Import({LicenseGrantServiceImpl.class,
        cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionServiceImpl.class})
public class LicenseGrantRevokeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private LicenseGrantService licenseGrantService;
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
    @Resource
    private MemberLicenseGrantMapper grantMapper;

    // ==================== 工具 ====================

    private MemberUserDO createUser() {
        MemberUserDO user = MemberUserDO.builder()
                .nickname("测试会员").mobile("13800009999").status(0)
                .avatar("").registerIp("127.0.0.1").build();
        memberUserMapper.insert(user);
        return user;
    }

    private MaxLabelOrgDO createOrg() {
        MaxLabelOrgDO org = MaxLabelOrgDO.builder()
                .name("测试企业" + System.nanoTime()).status(0).ownerMemberId(1L).build();
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

    private LicenseGrantReqVO memberReq(Long memberId, int months) {
        LicenseGrantReqVO reqVO = new LicenseGrantReqVO();
        reqVO.setTargetType(MemberLicenseGrantDO.TARGET_TYPE_MEMBER);
        reqVO.setTargetId(memberId);
        reqVO.setContentType(MemberLicenseGrantDO.CONTENT_TYPE_MONTHS);
        reqVO.setMonths(months);
        reqVO.setReason("测试补偿");
        return reqVO;
    }

    private LicenseGrantReqVO orgSeatReq(Long orgId, Integer months, int seats) {
        LicenseGrantReqVO reqVO = new LicenseGrantReqVO();
        reqVO.setTargetType(MemberLicenseGrantDO.TARGET_TYPE_ORG);
        reqVO.setTargetId(orgId);
        reqVO.setContentType(MemberLicenseGrantDO.CONTENT_TYPE_SEATS);
        reqVO.setMonths(months);
        reqVO.setSeats(seats);
        reqVO.setReason("企业补席位");
        return reqVO;
    }

    private LicenseGrantReqVO orgMonthReq(Long orgId, int months) {
        LicenseGrantReqVO reqVO = new LicenseGrantReqVO();
        reqVO.setTargetType(MemberLicenseGrantDO.TARGET_TYPE_ORG);
        reqVO.setTargetId(orgId);
        reqVO.setContentType(MemberLicenseGrantDO.CONTENT_TYPE_MONTHS);
        reqVO.setMonths(months);
        reqVO.setReason("企业补时长");
        return reqVO;
    }

    private LicenseGrantRevokeReqVO revokeReq(Long grantId) {
        LicenseGrantRevokeReqVO reqVO = new LicenseGrantRevokeReqVO();
        reqVO.setId(grantId);
        reqVO.setReason("误送给该客户");
        return reqVO;
    }

    private MemberLicenseGrantDO latestGrant() {
        return grantMapper.selectList(new LambdaQueryWrapperX<MemberLicenseGrantDO>()
                        .orderByDesc(MemberLicenseGrantDO::getId))
                .stream().findFirst().orElseThrow();
    }

    // ==================== 个人 ====================

    @Test
    public void testRevokeMember_restoresPreviousEndAndKeepsRecord() {
        MemberUserDO user = createUser();
        LocalDateTime currentEnd = LocalDate.now().plusMonths(2).atStartOfDay();
        MemberSubscriptionDO old = createSubscription(user.getId(), currentEnd);
        licenseGrantService.grant(memberReq(user.getId(), 1), 100L);
        MemberLicenseGrantDO grant = latestGrant();

        assertTrue(licenseGrantService.revoke(revokeReq(grant.getId()), 200L));

        // 这条赠送产生的订阅行已经不参与权益解析
        assertEquals(currentEnd, maxActiveEnd(user.getId()), "撤回后到期日回到赠送前");
        assertNull(subscriptionMapper.selectByLicenseGrantId(grant.getId()));
        assertEquals(old.getId(), subscriptionMapper.selectListByMemberId(user.getId()).get(0).getId());
        // 赠送记录本身不删，只留撤回痕迹
        MemberLicenseGrantDO after = grantMapper.selectById(grant.getId());
        assertNotNull(after);
        assertNotNull(after.getRevokeTime());
        assertEquals(200L, after.getRevokeOperatorId());
        assertEquals("误送给该客户", after.getRevokeReason());
    }

    private LocalDateTime maxActiveEnd(Long memberId) {
        return subscriptionMapper.selectListByMemberId(memberId).stream()
                .map(MemberSubscriptionDO::getEndTime)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    @Test
    public void testRevokeMember_blockedWhenNotLatestGrant() {
        MemberUserDO user = createUser();
        licenseGrantService.grant(memberReq(user.getId(), 1), 100L);
        Long firstId = latestGrant().getId();
        // 换一个理由，避开 T088 的 10 秒连点幂等，才真的会落第二笔
        LicenseGrantReqVO second = memberReq(user.getId(), 1);
        second.setReason("第二次补偿");
        licenseGrantService.grant(second, 100L);

        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.revoke(revokeReq(firstId), 200L));
        assertEquals(LICENSE_GRANT_NOT_LATEST.getCode(), e.getCode());
    }

    @Test
    public void testRevokeMember_blockedWhenLaterSubscriptionExists() {
        MemberUserDO user = createUser();
        licenseGrantService.grant(memberReq(user.getId(), 1), 100L);
        MemberLicenseGrantDO grant = latestGrant();
        // 赠送之后客户又买了（新增一行订阅）——删中间段会留下空档
        createSubscription(user.getId(), LocalDate.now().plusMonths(6).atStartOfDay());

        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.revoke(revokeReq(grant.getId()), 200L));
        assertEquals(LICENSE_GRANT_REVOKE_CONFLICT.getCode(), e.getCode());
        assertNull(grantMapper.selectById(grant.getId()).getRevokeTime(), "被拒时不留撤回痕迹");
    }

    @Test
    public void testRevoke_alreadyRevoked() {
        MemberUserDO user = createUser();
        licenseGrantService.grant(memberReq(user.getId(), 1), 100L);
        Long grantId = latestGrant().getId();
        licenseGrantService.revoke(revokeReq(grantId), 200L);

        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.revoke(revokeReq(grantId), 200L));
        assertEquals(LICENSE_GRANT_ALREADY_REVOKED.getCode(), e.getCode());
    }

    @Test
    public void testRevoke_reasonRequired() {
        MemberUserDO user = createUser();
        licenseGrantService.grant(memberReq(user.getId(), 1), 100L);
        LicenseGrantRevokeReqVO reqVO = revokeReq(latestGrant().getId());
        reqVO.setReason("   ");

        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.revoke(reqVO, 200L));
        assertEquals(LICENSE_GRANT_REVOKE_REASON_REQUIRED.getCode(), e.getCode());
    }

    // ==================== 企业 ====================

    @Test
    public void testRevokeOrgSeats_reducesSeatsTotal() {
        MaxLabelOrgDO org = createOrg();
        createLicense(org.getId(), 10, LocalDate.now().plusMonths(3).atStartOfDay());
        licenseGrantService.grant(orgSeatReq(org.getId(), null, 2), 100L);
        MemberLicenseGrantDO grant = latestGrant();
        assertEquals(12, licenseMapper.selectListByOrgId(org.getId()).get(0).getSeatsTotal());

        assertTrue(licenseGrantService.revoke(revokeReq(grant.getId()), 200L));

        assertEquals(10, licenseMapper.selectListByOrgId(org.getId()).get(0).getSeatsTotal(),
                "席位回减到赠送前");
    }

    @Test
    public void testRevokeOrgSeats_blockedWhenSeatAssigned() {
        // 企业原本没有批次：赠送建出的批次里 2 个席位，已分出去 1 个就不能整批收回
        MaxLabelOrgDO org = createOrg();
        licenseGrantService.grant(orgSeatReq(org.getId(), 12, 2), 100L);
        MemberLicenseGrantDO grant = latestGrant();
        MemberUserDO member = createUser();
        seatMapper.insert(MaxLabelOrgSeatDO.builder().orgId(org.getId()).memberId(member.getId())
                .status(0).operatorType(1).operatorId(member.getId())
                .assignedTime(LocalDateTime.now()).build());

        ServiceException e = assertThrows(ServiceException.class,
                () -> licenseGrantService.revoke(revokeReq(grant.getId()), 200L));
        assertEquals(LICENSE_GRANT_SEATS_IN_USE.getCode(), e.getCode());
        assertEquals(2, licenseMapper.selectListByOrgId(org.getId()).get(0).getSeatsTotal(),
                "被拒时不改席位数");
    }

    @Test
    public void testRevokeOrgMonths_restoresEndTimeAndPeriodMonths() {
        MaxLabelOrgDO org = createOrg();
        LocalDateTime currentEnd = LocalDate.now().plusMonths(3).atStartOfDay();
        MaxLabelOrgLicenseDO batch = createLicense(org.getId(), 10, currentEnd);
        licenseGrantService.grant(orgMonthReq(org.getId(), 3), 100L);
        MemberLicenseGrantDO grant = latestGrant();
        assertEquals(currentEnd.plusMonths(3), licenseMapper.selectById(batch.getId()).getEndTime());

        assertTrue(licenseGrantService.revoke(revokeReq(grant.getId()), 200L));

        MaxLabelOrgLicenseDO after = licenseMapper.selectById(batch.getId());
        assertEquals(currentEnd, after.getEndTime(), "到期日退回赠送前");
        assertEquals(12, after.getPeriodMonths(), "period_months 同步退回，不能只改 end_time");
    }

    @Test
    public void testRevokeOrg_createdBatchDeleted() {
        MaxLabelOrgDO org = createOrg();
        licenseGrantService.grant(orgSeatReq(org.getId(), 12, 5), 100L);
        MemberLicenseGrantDO grant = latestGrant();
        Long batchId = licenseMapper.selectListByOrgId(org.getId()).get(0).getId();

        assertTrue(licenseGrantService.revoke(revokeReq(grant.getId()), 200L));

        assertNull(licenseMapper.selectById(batchId), "这笔赠送新建的批次整批撤回");
    }

}
