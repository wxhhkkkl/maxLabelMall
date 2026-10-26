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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业授权批次：周期取计划周期（不乘席位数）、续费顺延、幂等、退款只标记。
 */
@Import({MaxLabelOrgLicenseServiceImpl.class, MaxLabelOrgServiceImpl.class})
public class MaxLabelOrgLicenseServiceImplTest extends BaseDbUnitTest {

    @Resource
    private MaxLabelOrgLicenseServiceImpl licenseService;
    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgLicenseMapper licenseMapper;
    @Resource
    private MemberUserMapper userMapper;

    private Long orgId;

    @BeforeEach
    void setUp() {
        MemberUserDO owner = MemberUserDO.builder()
                .mobile("13800138001").nickname("企业主").status(0).registerIp("127.0.0.1").build();
        userMapper.insert(owner);
        orgId = orgService.createOrg("测试企业", owner.getId(), "");
    }

    private MemberOrgLicenseGrantReqDTO request(int months, int seats, Long itemId) {
        MemberOrgLicenseGrantReqDTO req = new MemberOrgLicenseGrantReqDTO();
        req.setOrgId(orgId);
        req.setPlanId(11L);
        req.setSpuId(500L);
        req.setSkuId(501L);
        req.setPeriodMonths(months);
        req.setSeatsTotal(seats);
        req.setOrderId(8001L);
        req.setOrderItemId(itemId);
        req.setOrderNo("o8001");
        return req;
    }

    @Test
    void grantUsesPlanPeriodAndSeatCountDoesNotMultiplyPeriod() {
        LocalDate today = LocalDate.now();
        MaxLabelOrgLicenseDO row = licenseService.grant(request(12, 2, 9001L));

        assertEquals(today.atStartOfDay(), row.getStartTime());
        // 2 个席位 = 2 个席位，不是 24 个月
        assertEquals(today.plusMonths(12).plusDays(1).atStartOfDay(), row.getEndTime());
        assertEquals(12, row.getPeriodMonths());
        assertEquals(2, row.getSeatsTotal());
        assertFalse(row.getRefunded());
    }

    @Test
    void renewalStartsFromPreviousEndDate() {
        MaxLabelOrgLicenseDO first = licenseService.grant(request(12, 2, 9001L));
        MaxLabelOrgLicenseDO second = licenseService.grant(request(12, 5, 9002L));

        // 从原到期日（end_time 的前一天）接续，避免每次续费多 1 天
        LocalDate originalEndDate = first.getEndTime().toLocalDate().minusDays(1);
        assertEquals(originalEndDate, second.getStartTime().toLocalDate());
        assertEquals(originalEndDate.plusMonths(12).plusDays(1).atStartOfDay(),
                second.getEndTime());
        assertEquals(5, second.getSeatsTotal());
    }

    @Test
    void renewalAfterExpiryStartsToday() {
        MaxLabelOrgLicenseDO first = licenseService.grant(request(1, 1, 9001L));
        // 把第一批改到过去
        licenseMapper.updateById(new MaxLabelOrgLicenseDO().setId(first.getId())
                .setEndTime(LocalDateTime.now().minusDays(1)));

        MaxLabelOrgLicenseDO second = licenseService.grant(request(1, 1, 9002L));
        assertEquals(LocalDate.now().atStartOfDay(), second.getStartTime());
    }

    @Test
    void threeConsecutiveRenewalsExtendBy36Months() {
        licenseService.grant(request(12, 2, 9001L));
        licenseService.grant(request(12, 2, 9002L));
        MaxLabelOrgLicenseDO third = licenseService.grant(request(12, 2, 9003L));

        assertEquals(LocalDate.now().plusMonths(36),
                third.getEndTime().toLocalDate().minusDays(1));
    }

    @Test
    void sameOrderItemIsIdempotent() {
        MaxLabelOrgLicenseDO first = licenseService.grant(request(12, 2, 9001L));
        MaxLabelOrgLicenseDO again = licenseService.grant(request(12, 2, 9001L));
        assertEquals(first.getId(), again.getId());
        assertEquals(1, licenseMapper.selectListByOrgId(orgId).size());
    }

    @Test
    void zeroSeatsRejected() {
        assertThrows(Exception.class, () -> licenseService.grant(request(12, 0, 9001L)));
    }

    @Test
    void refundOnlyMarksNotShortens() {
        MaxLabelOrgLicenseDO row = licenseService.grant(request(12, 2, 9001L));
        licenseService.markRefundedByOrderItem(9001L);

        MaxLabelOrgLicenseDO after = licenseMapper.selectById(row.getId());
        assertTrue(after.getRefunded());
        assertEquals(row.getEndTime(), after.getEndTime());
        assertEquals(2, after.getSeatsTotal());
    }

}
