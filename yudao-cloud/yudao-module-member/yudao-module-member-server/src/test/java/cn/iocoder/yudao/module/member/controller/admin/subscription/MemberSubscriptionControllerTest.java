package cn.iocoder.yudao.module.member.controller.admin.subscription;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.subscription.vo.MemberSubscriptionPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.subscription.vo.MemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MemberSubscriptionControllerTest {

    @Mock
    private MemberSubscriptionService subscriptionService;
    @InjectMocks
    private MemberSubscriptionController controller;

    private MemberSubscriptionPageReqVO request() {
        MemberSubscriptionPageReqVO req = new MemberSubscriptionPageReqVO();
        req.setUserId(1001L);
        req.setPageNo(1);
        req.setPageSize(10);
        return req;
    }

    @Test
    void pageMapsActiveWindowAndRefundFlag() {
        LocalDate today = LocalDate.now();
        MemberSubscriptionDO row = MemberSubscriptionDO.builder()
                .id(1L).memberId(1001L).periodMonths(12)
                .startTime(today.atStartOfDay())
                .endTime(today.plusMonths(12).plusDays(1).atStartOfDay())
                .sourceOrderId(8001L).sourceOrderNo("o8001").refunded(true)
                .build();
        MemberSubscriptionPageReqVO req = request();
        PageResult<MemberSubscriptionDO> page = new PageResult<>(List.of(row), 1L);
        when(subscriptionService.getPageByMemberId(1001L, req)).thenReturn(page);

        CommonResult<PageResult<MemberSubscriptionRespVO>> result = controller.getPage(req);
        MemberSubscriptionRespVO vo = result.getData().getList().get(0);
        assertTrue(vo.getActive());
        assertEquals(today, vo.getStartDate());
        assertEquals(today.plusMonths(12), vo.getEndDate());
        assertEquals("o8001", vo.getSourceOrderNo());
        assertTrue(vo.getRefunded());
    }

    @Test
    void pageMarksExpiredWhenEndTimeIsTodayMidnight() {
        LocalDate today = LocalDate.now();
        MemberSubscriptionDO row = MemberSubscriptionDO.builder()
                .id(2L).memberId(1001L).periodMonths(1)
                .startTime(today.minusMonths(1).atStartOfDay())
                .endTime(today.atStartOfDay())
                .build();
        MemberSubscriptionPageReqVO req = request();
        PageResult<MemberSubscriptionDO> page = new PageResult<>(List.of(row), 1L);
        when(subscriptionService.getPageByMemberId(1001L, req)).thenReturn(page);

        MemberSubscriptionRespVO vo = controller.getPage(req).getData().getList().get(0);
        assertFalse(vo.getActive());
        assertEquals(today.minusDays(1), vo.getEndDate());
        assertEquals(0L, vo.getRemainingDays());
    }

}
