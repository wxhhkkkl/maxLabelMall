package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgLicenseGrantReqDTO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgLicenseMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业席位：容量、分配/撤销、跨企业、旧批次过期不打断已分配席位。
 */
@Import({MaxLabelOrgSeatServiceImpl.class, MaxLabelOrgMemberServiceImpl.class,
        MaxLabelOrgLicenseServiceImpl.class, MaxLabelOrgServiceImpl.class})
public class MaxLabelOrgSeatServiceImplTest extends BaseDbUnitTest {

    private static final int OPERATOR_MEMBER = 1;

    @Resource
    private MaxLabelOrgSeatService seatService;
    @Resource
    private MaxLabelOrgMemberService memberService;
    @Resource
    private MaxLabelOrgLicenseService licenseService;
    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgLicenseMapper licenseMapper;
    @Resource
    private MemberUserMapper userMapper;

    private Long orgId;
    private Long ownerId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        MemberUserDO owner = MemberUserDO.builder()
                .mobile("13800138002").nickname("企业主").status(0).registerIp("127.0.0.1").build();
        userMapper.insert(owner);
        ownerId = owner.getId();
        orgId = orgService.createOrg("席位测试企业", ownerId, "");

        MemberUserDO member = MemberUserDO.builder()
                .mobile("13800138003").nickname("普通成员").status(0).registerIp("127.0.0.1").build();
        userMapper.insert(member);
        memberId = member.getId();
        memberService.addMember(orgId, "13800138003", OPERATOR_MEMBER, ownerId);
    }

    private void grant(int seats, Long itemId) {
        MemberOrgLicenseGrantReqDTO req = new MemberOrgLicenseGrantReqDTO();
        req.setOrgId(orgId);
        req.setPlanId(11L);
        req.setSpuId(500L);
        req.setSkuId(501L);
        req.setPeriodMonths(12);
        req.setSeatsTotal(seats);
        req.setOrderId(8001L);
        req.setOrderItemId(itemId);
        req.setOrderNo("o8001");
        licenseService.grant(req);
    }

    @Test
    void assignAndRevokeChangesCapacity() {
        grant(2, 9001L);
        assertEquals(2, seatService.getAvailableSeats(orgId));

        seatService.assign(orgId, memberId, OPERATOR_MEMBER, ownerId);
        assertNotNull(seatService.getActiveByMemberId(memberId));
        assertEquals(1, seatService.getAvailableSeats(orgId));

        seatService.revoke(orgId, memberId, OPERATOR_MEMBER, ownerId);
        assertNull(seatService.getActiveByMemberId(memberId));
        assertEquals(2, seatService.getAvailableSeats(orgId));
    }

    @Test
    void duplicateAssignRejected() {
        grant(2, 9001L);
        seatService.assign(orgId, memberId, OPERATOR_MEMBER, ownerId);
        assertThrows(Exception.class,
                () -> seatService.assign(orgId, memberId, OPERATOR_MEMBER, ownerId));
    }

    @Test
    void fullCapacityRejected() {
        grant(1, 9001L);
        seatService.assign(orgId, memberId, OPERATOR_MEMBER, ownerId);

        MemberUserDO other = MemberUserDO.builder()
                .mobile("13800138004").nickname("另一成员").status(0).registerIp("127.0.0.1").build();
        userMapper.insert(other);
        memberService.addMember(orgId, "13800138004", OPERATOR_MEMBER, ownerId);

        assertThrows(Exception.class,
                () -> seatService.assign(orgId, other.getId(), OPERATOR_MEMBER, ownerId));
    }

    @Test
    void oldBatchExpiryDoesNotRevokeAssignedSeat() {
        grant(1, 9001L);
        seatService.assign(orgId, memberId, OPERATOR_MEMBER, ownerId);
        // 旧批次过期
        MaxLabelOrgLicenseDO old = licenseMapper.selectListByOrgId(orgId).get(0);
        licenseMapper.updateById(new MaxLabelOrgLicenseDO().setId(old.getId())
                .setEndTime(LocalDateTime.now().minusDays(1)));

        // 续费一个新批次；已分配席位仍在
        grant(1, 9002L);
        assertNotNull(seatService.getActiveByMemberId(memberId));
        assertEquals(0, seatService.getAvailableSeats(orgId));
    }

    @Test
    void allBatchesExpiredMakesSeatIneffectiveButRecordRetained() {
        grant(1, 9001L);
        seatService.assign(orgId, memberId, OPERATOR_MEMBER, ownerId);
        MaxLabelOrgLicenseDO old = licenseMapper.selectListByOrgId(orgId).get(0);
        licenseMapper.updateById(new MaxLabelOrgLicenseDO().setId(old.getId())
                .setEndTime(LocalDateTime.now().minusDays(1)));

        assertEquals(0, seatService.getAvailableSeats(orgId));
        // 记录仍在（status=0），但权益解析时会因无未过期批次而失效
        assertNotNull(seatService.getActiveByMemberId(memberId));
    }

}
