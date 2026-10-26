package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
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

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_ORG_RENEWAL_SEATS_TOO_FEW;
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
    void renewalExtendsFromPreviousEndDateAndFoldsTheOldBatch() {
        MaxLabelOrgLicenseDO first = licenseService.grant(request(12, 2, 9001L));
        MaxLabelOrgLicenseDO second = licenseService.grant(request(12, 2, 9002L));

        // 纯续费（席位数不变）：到期日 = 原到期日 + 一个周期，误差 0 天（SC-107）
        assertEquals(LocalDate.now().atStartOfDay(), second.getStartTime());
        assertEquals(first.getEndTime().plusMonths(12), second.getEndTime());
        assertEquals(2, second.getSeatsTotal());
        // 旧批次已折算：不再提供容量与到期日
        assertNotNull(licenseMapper.selectById(first.getId()).getFoldedTime());
        assertEquals(second.getEndTime(), licenseService.getMaxEndTime(orgId));
        assertEquals(2, licenseService.getSeatTotal(orgId));
    }

    @Test
    void expansionFoldsRemainingTimeIntoTheNewSeatCount() {
        MaxLabelOrgLicenseDO first = licenseService.grant(request(12, 1, 9001L));
        // 造出「原 1 席位还剩 10 个月」
        licenseMapper.updateById(new MaxLabelOrgLicenseDO().setId(first.getId())
                .setEndTime(LocalDate.now().plusMonths(10).plusDays(1).atStartOfDay()));

        MaxLabelOrgLicenseDO second = licenseService.grant(request(12, 2, 9002L));

        // (1 席 × 10 个月 + 2 席 × 12 个月) / 2 席 = 17 个月
        assertEquals(LocalDate.now().atStartOfDay(), second.getStartTime());
        assertEquals(LocalDate.now().plusMonths(17).plusDays(1).atStartOfDay(),
                second.getEndTime());
        assertEquals(2, second.getSeatsTotal());
        assertEquals(2, licenseService.getSeatTotal(orgId));
        assertNotNull(licenseMapper.selectById(first.getId()).getFoldedTime());
    }

    @Test
    void foldedBatchProvidesNoSeatsOrEndTimeEvenIfItEndsLater() {
        MaxLabelOrgLicenseDO active = licenseService.grant(request(12, 2, 9001L));
        // 折算批次的 end_time 更晚时，也 MUST NOT 把容量/到期日顶上去
        licenseMapper.insert(MaxLabelOrgLicenseDO.builder()
                .orgId(orgId).planId(11L).spuId(500L).skuId(501L)
                .periodMonths(12).seatsTotal(9)
                .startTime(LocalDate.now().atStartOfDay())
                .endTime(LocalDate.now().plusMonths(36).plusDays(1).atStartOfDay())
                .sourceOrderId(8002L).sourceOrderItemId(9002L).sourceOrderNo("o9002")
                .refunded(false).foldedTime(LocalDateTime.now()).build());

        assertEquals(2, licenseService.getSeatTotal(orgId));
        assertEquals(active.getEndTime(), licenseService.getMaxEndTime(orgId));
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
    void renewalCannotBuyFewerSeatsThanCurrent() {
        licenseService.grant(request(12, 2, 9001L));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> licenseService.grant(request(12, 1, 9002L)));
        assertEquals(MEMBER_ORG_RENEWAL_SEATS_TOO_FEW.getCode(), exception.getCode());
    }

    @Test
    void smallerOrRefundedRenewalBatchDoesNotReduceCurrentSeats() {
        MaxLabelOrgLicenseDO first = licenseService.grant(request(12, 2, 9001L));
        // 模拟历史脏数据：续费只买了 1 席，且到期日更晚
        MaxLabelOrgLicenseDO extra = MaxLabelOrgLicenseDO.builder()
                .orgId(orgId).planId(11L).spuId(500L).skuId(501L)
                .periodMonths(12).seatsTotal(1)
                .startTime(LocalDate.now().plusMonths(12).atStartOfDay())
                .endTime(LocalDate.now().plusMonths(24).plusDays(1).atStartOfDay())
                .sourceOrderId(8002L).sourceOrderItemId(9002L).sourceOrderNo("o9002")
                .refunded(false).build();
        licenseMapper.insert(extra);

        // 当前席位取未退款批次的最大值，不能被 1 席的晚期批次拉低
        assertEquals(2, licenseService.getSeatTotal(orgId));

        // 退款后该批次不再提供容量，仍是 2；到期日也回到未退款批次
        licenseMapper.updateById(new MaxLabelOrgLicenseDO().setId(extra.getId()).setRefunded(true));
        assertEquals(2, licenseService.getSeatTotal(orgId));
        assertEquals(first.getEndTime(), licenseService.getMaxEndTime(orgId));
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
