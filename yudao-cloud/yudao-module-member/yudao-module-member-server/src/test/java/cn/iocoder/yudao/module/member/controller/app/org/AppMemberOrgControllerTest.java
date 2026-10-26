package cn.iocoder.yudao.module.member.controller.app.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.controller.app.org.vo.AppMemberOrgLicenseRespVO;
import cn.iocoder.yudao.module.member.controller.app.org.vo.AppMemberOrgMemberRespVO;
import cn.iocoder.yudao.module.member.controller.app.org.vo.AppMemberOrgRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgMemberService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppMemberOrgControllerTest {

    @Mock
    private MaxLabelOrgService orgService;
    @Mock
    private MaxLabelOrgMemberService memberService;
    @Mock
    private MaxLabelOrgSeatService seatService;
    @Mock
    private MaxLabelOrgLicenseService licenseService;
    @InjectMocks
    private AppMemberOrgController controller;

    private MemberOrgRespDTO org(int role) {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setOrgName("测试企业");
        org.setRole(role);
        org.setStatus(0);
        return org;
    }

    @Test
    void getMyOrg_returnsOverviewWithSeatAndCapacity() {
        when(orgService.getMemberOrg(1001L)).thenReturn(org(1));
        when(licenseService.getMaxEndTime(88L))
                .thenReturn(LocalDateTime.of(2027, 10, 27, 0, 0));
        when(licenseService.getSeatTotal(88L)).thenReturn(20);
        when(seatService.getActiveCount(88L)).thenReturn(3);
        when(seatService.getAvailableSeats(88L)).thenReturn(17);
        when(memberService.getMemberCount(88L)).thenReturn(5);
        when(seatService.getActiveByMemberId(1001L)).thenReturn(
                MaxLabelOrgSeatDO.builder().orgId(88L).memberId(1001L).status(0).build());

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            AppMemberOrgRespVO resp = controller.getMyOrg().getData();
            assertEquals(88L, resp.getOrgId());
            assertEquals("测试企业", resp.getOrgName());
            assertTrue(resp.getSeatActive());
            assertEquals(LocalDate.of(2027, 10, 26), resp.getSeatEndDate());
            assertEquals(20, resp.getSeatTotal());
            assertEquals(3, resp.getSeatUsed());
            assertEquals(17, resp.getSeatAvailable());
            assertEquals(5L, resp.getMemberCount());
        }
    }

    @Test
    void getMyOrg_noOrgReturnsNullOrgId() {
        when(orgService.getMemberOrg(1001L)).thenReturn(null);
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            AppMemberOrgRespVO resp = controller.getMyOrg().getData();
            assertNull(resp.getOrgId());
            assertNull(resp.getSeatActive());
        }
    }

    @Test
    void getLicensePage_mapsBatchState() {
        when(orgService.requireOrgAdmin(1001L)).thenReturn(org(1));
        MaxLabelOrgLicenseDO row = MaxLabelOrgLicenseDO.builder()
                .id(1L).orgId(88L).skuId(601L).periodMonths(12).seatsTotal(20)
                .startTime(LocalDateTime.now().minusMonths(1))
                .endTime(LocalDate.now().plusMonths(11).plusDays(1).atStartOfDay())
                .sourceOrderNo("o1").refunded(false).build();
        when(licenseService.getPage(eq(88L), any(PageParam.class)))
                .thenReturn(new PageResult<>(List.of(row), 1L));

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var page = controller.getLicensePage(new PageParam()).getData();
            assertEquals(1L, page.getTotal());
            assertEquals(601L, page.getList().get(0).getSkuId());
            assertTrue(page.getList().get(0).getActive());
        }
    }

    @Test
    void leave_delegatesToMemberService() {
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            controller.leave();
            verify(memberService).leave(1001L);
        }
    }

    @Test
    void enterpriseResponseVosContainNoMemberPrivateData() {
        // US46：企业侧只允许成员标识/角色/席位/批次，不得出现模板、共享或打印字段
        for (Class<?> type : List.of(AppMemberOrgRespVO.class, AppMemberOrgMemberRespVO.class,
                AppMemberOrgLicenseRespVO.class)) {
            for (var field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                assertFalse(name.contains("template"), type.getSimpleName() + "." + name);
                assertFalse(name.contains("print"), type.getSimpleName() + "." + name);
                assertFalse(name.contains("content"), type.getSimpleName() + "." + name);
            }
        }
    }

}
