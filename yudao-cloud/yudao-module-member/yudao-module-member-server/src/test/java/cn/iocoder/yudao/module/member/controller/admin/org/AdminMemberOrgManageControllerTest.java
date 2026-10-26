package cn.iocoder.yudao.module.member.controller.admin.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.controller.admin.org.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgLicenseDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminMemberOrgManageControllerTest {

    private static final long SYSTEM_USER_ID = 9001L;

    @Mock
    private MaxLabelOrgService orgService;
    @Mock
    private MaxLabelOrgMemberService memberService;
    @Mock
    private MaxLabelOrgSeatService seatService;
    @Mock
    private MaxLabelOrgLicenseService licenseService;
    @Mock
    private MemberUserMapper memberUserMapper;
    @InjectMocks
    private AdminMemberOrgManageController controller;

    @Test
    void memberAndSeatOperations_delegateAsSystemOperator() {
        MaxLabelOrgMemberAddReqVO addReq = new MaxLabelOrgMemberAddReqVO();
        addReq.setOrgId(88L);
        addReq.setMobile("13800138000");
        MaxLabelOrgMemberIdReqVO idReq = new MaxLabelOrgMemberIdReqVO();
        idReq.setOrgId(88L);
        idReq.setMemberId(1002L);
        MaxLabelOrgMemberRoleReqVO roleReq = new MaxLabelOrgMemberRoleReqVO();
        roleReq.setOrgId(88L);
        roleReq.setMemberId(1002L);
        roleReq.setRole(2);

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(SYSTEM_USER_ID);
            controller.addMember(addReq);
            controller.removeMember(idReq);
            controller.updateRole(roleReq);
            controller.transferOwner(idReq);
            controller.assignSeat(idReq);
            controller.revokeSeat(idReq);
        }

        verify(memberService).addMember(88L, "13800138000", 2, SYSTEM_USER_ID);
        verify(memberService).removeMember(88L, 1002L, 2, SYSTEM_USER_ID);
        verify(memberService).updateRole(88L, 1002L, 2, 2, SYSTEM_USER_ID);
        verify(memberService).transferOwner(88L, 1002L, 2, SYSTEM_USER_ID);
        verify(seatService).assign(88L, 1002L, 2, SYSTEM_USER_ID);
        verify(seatService).revoke(88L, 1002L, 2, SYSTEM_USER_ID);
    }

    @Test
    void licensePage_mapsBatch() {
        MaxLabelOrgLicensePageReqVO req = new MaxLabelOrgLicensePageReqVO();
        req.setOrgId(88L);
        MaxLabelOrgLicenseDO row = MaxLabelOrgLicenseDO.builder()
                .id(1L).orgId(88L).skuId(601L).periodMonths(12).seatsTotal(20)
                .startTime(LocalDateTime.now().minusMonths(1))
                .endTime(LocalDateTime.now().plusMonths(12))
                .sourceOrderNo("o1").refunded(false).build();
        when(licenseService.getPage(eq(88L), any()))
                .thenReturn(new PageResult<>(List.of(row), 1L));

        var page = controller.getLicensePage(req).getData();
        assertEquals(1L, page.getTotal());
        assertEquals(601L, page.getList().get(0).getSkuId());
    }

    @Test
    void getByMember_mapsOrgAndSeat() {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setOrgName("测试企业");
        org.setRole(3);
        org.setStatus(0);
        when(orgService.getMemberOrg(1002L)).thenReturn(org);
        when(licenseService.getMaxEndTime(88L)).thenReturn(LocalDateTime.now().plusMonths(12));
        when(seatService.getActiveByMemberId(1002L)).thenReturn(
                cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO.builder()
                        .orgId(88L).memberId(1002L).status(0).build());

        var resp = controller.getByMember(1002L).getData();
        assertEquals("测试企业", resp.getOrgName());
        assertEquals(3, resp.getRole());
        assertEquals(Boolean.TRUE, resp.getSeatActive());
    }

    @Test
    void adminEnterpriseVosContainNoMemberPrivateData() {
        for (Class<?> type : List.of(MaxLabelOrgLicenseRespVO.class, MaxLabelOrgMemberInfoRespVO.class)) {
            for (var field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                assertFalse(name.contains("template"), type.getSimpleName() + "." + name);
                assertFalse(name.contains("print"), type.getSimpleName() + "." + name);
                assertFalse(name.contains("content"), type.getSimpleName() + "." + name);
            }
        }
    }

}
