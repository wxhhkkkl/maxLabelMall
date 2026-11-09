package cn.iocoder.yudao.module.member.controller.app.share;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.member.controller.app.share.vo.ShareSpaceOverviewRespVO;
import cn.iocoder.yudao.module.member.service.share.ShareSpaceService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppShareSpaceControllerTest {

    private static final Long MEMBER_ID = 1001L;

    @Mock private ShareSpaceService spaceService;
    @InjectMocks private AppShareSpaceController controller;

    @BeforeEach
    void login() {
        LoginUser user = new LoginUser();
        user.setId(MEMBER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void overviewUsesLoggedInMember() {
        ShareSpaceOverviewRespVO overview = new ShareSpaceOverviewRespVO();
        ShareSpaceOverviewRespVO.Personal personal = new ShareSpaceOverviewRespVO.Personal();
        personal.setEdition("standard");
        overview.setPersonal(personal);
        when(spaceService.getOverview(MEMBER_ID)).thenReturn(overview);

        assertEquals("standard", controller.getOverview().getData().getPersonal().getEdition());
    }

}
