package cn.iocoder.yudao.module.member.controller.app.org;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.controller.app.org.vo.AppMemberOrgMemberIdReqVO;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppMemberOrgSeatControllerTest {

    @Mock
    private MaxLabelOrgService orgService;
    @Mock
    private MaxLabelOrgSeatService seatService;
    @InjectMocks
    private AppMemberOrgSeatController controller;

    private AppMemberOrgMemberIdReqVO req() {
        AppMemberOrgMemberIdReqVO req = new AppMemberOrgMemberIdReqVO();
        req.setMemberId(1002L);
        return req;
    }

    private MemberOrgRespDTO adminOrg() {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setRole(2);
        org.setStatus(0);
        return org;
    }

    @Test
    void assign_delegatesWithMemberOperator() {
        when(orgService.requireOrgAdmin(1001L)).thenReturn(adminOrg());
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            controller.assign(req());
            verify(seatService).assign(88L, 1002L, 1, 1001L);
        }
    }

    @Test
    void revoke_delegatesWithMemberOperator() {
        when(orgService.requireOrgAdmin(1001L)).thenReturn(adminOrg());
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            controller.revoke(req());
            verify(seatService).revoke(88L, 1002L, 1, 1001L);
        }
    }

}
