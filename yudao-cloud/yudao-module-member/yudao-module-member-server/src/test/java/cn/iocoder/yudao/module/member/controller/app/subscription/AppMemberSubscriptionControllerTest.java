package cn.iocoder.yudao.module.member.controller.app.subscription;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppMemberSubscriptionControllerTest {

    @Mock
    private MemberSubscriptionService subscriptionService;
    @InjectMocks
    private AppMemberSubscriptionController controller;

    @BeforeEach
    void login() {
        LoginUser user = new LoginUser();
        user.setId(1001L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsCurrentSubscriptionForLoggedInMember() {
        AppMemberSubscriptionRespVO vo = new AppMemberSubscriptionRespVO();
        vo.setMemberId(1001L);
        vo.setActive(true);
        when(subscriptionService.getCurrent(1001L)).thenReturn(vo);

        AppMemberSubscriptionRespVO result = controller.getCurrent().getData();
        assertTrue(result.getActive());
        assertTrue(result.getMemberId() == 1001L);
    }

    @Test
    void returnsInactiveSubscriptionAfterExpiry() {
        AppMemberSubscriptionRespVO vo = new AppMemberSubscriptionRespVO();
        vo.setMemberId(1001L);
        vo.setActive(false);
        when(subscriptionService.getCurrent(1001L)).thenReturn(vo);

        assertFalse(controller.getCurrent().getData().getActive());
    }

}
