package cn.iocoder.yudao.module.member.controller.app.template;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateSaveReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import cn.iocoder.yudao.module.member.dal.mysql.template.MaxLabelSharedTemplateMapper;
import cn.iocoder.yudao.module.member.service.template.LabelTemplateService;
import cn.iocoder.yudao.module.member.service.template.SharedTemplateService;
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
public class AppLabelTemplateControllerTest {

    @Mock
    private LabelTemplateService templateService;
    @Mock
    private SharedTemplateService sharedTemplateService;
    @Mock
    private MaxLabelSharedTemplateMapper sharedTemplateMapper;
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
    void createUsesLoggedInMember() {
        AppLabelTemplateSaveReqVO req = new AppLabelTemplateSaveReqVO();
        req.setName("面单");
        req.setContent("{}");
        when(templateService.create(1001L, req)).thenReturn(9L);

        assertEquals(9L, controller.create(req).getData());
        verify(templateService).create(1001L, req);
    }

    @Test
    void getReturnsOwnedContent() {
        when(templateService.getContent(1001L, 9L)).thenReturn(MaxLabelLabelTemplateDO.builder()
                .id(9L).memberId(1001L).name("面单").content("{\"x\":1}").build());

        AppLabelTemplateContentRespVO vo = controller.get(9L).getData();
        assertEquals("面单", vo.getName());
        assertEquals("{\"x\":1}", vo.getContent());
    }

}
