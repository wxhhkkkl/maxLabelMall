package cn.iocoder.yudao.module.member.controller.app.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.controller.app.org.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgSeatDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppMemberOrgMemberControllerTest {

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
    private AppMemberOrgMemberController controller;

    private MemberOrgRespDTO adminOrg() {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setRole(1);
        org.setStatus(0);
        return org;
    }

    @Test
    void addMember_delegatesWithMemberOperator() {
        when(orgService.requireOrgAdmin(1001L)).thenReturn(adminOrg());
        AppMemberOrgMemberAddReqVO req = new AppMemberOrgMemberAddReqVO();
        req.setMobile("13800138000");
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            controller.addMember(req);
            verify(memberService).addMember(88L, "13800138000", 1, 1001L);
        }
    }

    @Test
    void removeRoleTransfer_delegate() {
        when(orgService.requireOrgAdmin(1001L)).thenReturn(adminOrg());
        AppMemberOrgMemberIdReqVO idReq = new AppMemberOrgMemberIdReqVO();
        idReq.setMemberId(1002L);
        AppMemberOrgMemberRoleReqVO roleReq = new AppMemberOrgMemberRoleReqVO();
        roleReq.setMemberId(1002L);
        roleReq.setRole(2);
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            controller.removeMember(idReq);
            controller.updateRole(roleReq);
            controller.transferOwner(idReq);
            verify(memberService).removeMember(88L, 1002L, 1, 1001L);
            verify(memberService).updateRole(88L, 1002L, 2, 1, 1001L);
            verify(memberService).transferOwner(88L, 1002L, 1, 1001L);
        }
    }

    @Test
    void memberPage_mapsSeatAndMasksMobile() {
        when(orgService.requireOrgAdmin(1001L)).thenReturn(adminOrg());
        MaxLabelOrgMemberDO relation = MaxLabelOrgMemberDO.builder()
                .orgId(88L).memberId(1002L).role(3).joinedTime(LocalDateTime.now()).build();
        when(memberService.getMemberPage(eq(88L), any(), any()))
                .thenReturn(new PageResult<>(List.of(relation), 1L));
        MemberUserDO user = MemberUserDO.builder().id(1002L).mobile("13800138000").nickname("张三").build();
        when(memberUserMapper.selectBatchIds(any())).thenReturn(List.of(user));
        when(seatService.getActiveList(88L)).thenReturn(List.of(
                MaxLabelOrgSeatDO.builder().orgId(88L).memberId(1002L).status(0).build()));
        when(licenseService.getMaxEndTime(88L))
                .thenReturn(LocalDateTime.now().plusMonths(12));

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var page = controller.getMemberPage(new AppMemberOrgMemberPageReqVO()).getData();
            assertEquals(1L, page.getTotal());
            assertEquals("张三", page.getList().get(0).getNickname());
            assertEquals("138****8000", page.getList().get(0).getMobileMasked());
            assertTrue(page.getList().get(0).getSeatActive());
        }
    }

}
