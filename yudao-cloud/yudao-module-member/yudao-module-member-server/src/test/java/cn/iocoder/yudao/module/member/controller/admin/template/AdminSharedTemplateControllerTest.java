package cn.iocoder.yudao.module.member.controller.admin.template;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminSharedTemplateAuditReqVO;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminSharedTemplateRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.template.SharedTemplateService;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminSharedTemplateControllerTest {

    @Mock
    private SharedTemplateService sharedTemplateService;
    @Mock
    private MemberUserService memberUserService;
    @InjectMocks
    private AdminSharedTemplateController controller;

    @BeforeEach
    void login() {
        LoginUser user = new LoginUser();
        user.setId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approveUsesLoggedInOperator() {
        controller.approve(7L);
        verify(sharedTemplateService).approve(7L, 1L);
    }

    @Test
    void rejectCarriesReasonAndOperator() {
        AdminSharedTemplateAuditReqVO req = new AdminSharedTemplateAuditReqVO();
        req.setId(7L);
        req.setReason("包含客户 logo");
        controller.reject(req);
        verify(sharedTemplateService).reject(7L, 1L, "包含客户 logo");
    }

    @Test
    void offlineUsesLoggedInOperator() {
        controller.offline(7L);
        verify(sharedTemplateService).offline(7L, 1L);
    }

    @Test
    void getMasksPublisherMobile() {
        when(sharedTemplateService.getAdmin(7L)).thenReturn(MaxLabelSharedTemplateDO.builder()
                .id(7L).publisherMemberId(1001L).name("面单").build());
        when(memberUserService.getUser(1001L)).thenReturn(MemberUserDO.builder()
                .id(1001L).nickname("张三").mobile("13800138000").build());

        AdminSharedTemplateRespVO vo = controller.get(7L).getData();
        assertEquals("张三", vo.getPublisherNickname());
        assertEquals("138****8000", vo.getPublisherMobile());
    }

}
