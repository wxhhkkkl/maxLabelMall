package cn.iocoder.yudao.module.member.controller.app.share;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareFolderMemberReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareFolderSaveReqVO;
import cn.iocoder.yudao.module.member.service.share.ShareFolderService;
import cn.iocoder.yudao.module.member.service.share.dto.ShareFolderInfo;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppShareFolderControllerTest {

    private static final Long MEMBER_ID = 1001L;

    @Mock private ShareFolderService folderService;
    @InjectMocks private AppShareFolderController controller;

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
    void createUsesLoggedInMember() {
        AppShareFolderSaveReqVO req = new AppShareFolderSaveReqVO();
        req.setName("财务");
        when(folderService.create(MEMBER_ID, "财务")).thenReturn(5L);
        assertEquals(5L, controller.create(req).getData());
    }

    @Test
    void listReturnsServiceResult() {
        ShareFolderInfo info = new ShareFolderInfo();
        info.setId(5L);
        info.setName("财务");
        when(folderService.getVisibleList(MEMBER_ID)).thenReturn(List.of(info));
        assertEquals("财务", controller.list().getData().get(0).getName());
    }

    @Test
    void createPropagatesNonAdminError() {
        AppShareFolderSaveReqVO req = new AppShareFolderSaveReqVO();
        req.setName("财务");
        when(folderService.create(MEMBER_ID, "财务"))
                .thenThrow(cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil
                        .exception(cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_ORG_NOT_ADMIN));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> controller.create(req));
    }

    @Test
    void deletePropagatesNotEmptyError() {
        org.mockito.Mockito.doThrow(cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil
                        .exception(cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_SHARE_FOLDER_NOT_EMPTY))
                .when(folderService).delete(MEMBER_ID, 5L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> controller.delete(5L));
    }

    @Test
    void updateMembersDelegates() {
        AppShareFolderMemberReqVO req = new AppShareFolderMemberReqVO();
        req.setId(5L);
        req.setMemberIds(List.of(1L, 2L));
        assertTrue(controller.updateMembers(req).getData());
        verify(folderService).updateMembers(MEMBER_ID, 5L, List.of(1L, 2L));
    }

}
