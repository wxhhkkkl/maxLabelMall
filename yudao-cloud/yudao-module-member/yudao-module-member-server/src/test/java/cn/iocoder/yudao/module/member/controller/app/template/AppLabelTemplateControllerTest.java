package cn.iocoder.yudao.module.member.controller.app.template;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentSaveReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateSaveReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.service.share.ShareContentService;
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

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppLabelTemplateControllerTest {

    @Mock
    private ShareContentService contentService;
    @InjectMocks
    private AppLabelTemplateController controller;

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
    void createUsesLoggedInMemberAndPersonalScope() {
        AppLabelTemplateSaveReqVO req = new AppLabelTemplateSaveReqVO();
        req.setName("面单.msdx");
        req.setContent("{}");
        when(contentService.create(eq(1001L), any(AppShareContentSaveReqVO.class))).thenReturn(9L);

        assertEquals(9L, controller.create(req).getData());
        org.mockito.ArgumentCaptor<AppShareContentSaveReqVO> captor =
                org.mockito.ArgumentCaptor.forClass(AppShareContentSaveReqVO.class);
        verify(contentService).create(eq(1001L), captor.capture());
        assertEquals("面单.msdx", captor.getValue().getName());
    }

    @Test
    void getReturnsOwnedContent() {
        when(contentService.getContent(1001L, 9L)).thenReturn(MaxLabelShareContentDO.builder()
                .id(9L).scope(1).ownerMemberId(1001L).contentType(1).name("面单.msdx")
                .content("{\"x\":1}").build());

        AppLabelTemplateContentRespVO vo = controller.get(9L).getData();
        assertEquals("面单", vo.getName());
        assertEquals("{\"x\":1}", vo.getContent());
        assertEquals(null, vo.getShareStatus());
    }

    @Test
    void submitShareIsClosed() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> controller.submitShare(new cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateSubmitShareReqVO()));
        assertEquals(MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED.getCode(), exception.getCode());
    }

}
