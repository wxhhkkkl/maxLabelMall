package cn.iocoder.yudao.module.member.controller.app.share;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentSaveReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppShareContentControllerTest {

    private static final Long MEMBER_ID = 1001L;

    @Mock private ShareContentService contentService;
    @Mock private MemberUserMapper memberUserMapper;
    @InjectMocks private AppShareContentController controller;

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
        AppShareContentSaveReqVO req = new AppShareContentSaveReqVO();
        when(contentService.create(eq(MEMBER_ID), any())).thenReturn(9L);
        assertEquals(9L, controller.create(req).getData());
    }

    @Test
    void pageMapsUploaderAndCanDelete() {
        AppShareContentPageReqVO req = new AppShareContentPageReqVO();
        req.setScope(1);
        MaxLabelShareContentDO content = MaxLabelShareContentDO.builder()
                .id(9L).scope(1).ownerMemberId(MEMBER_ID).contentType(1).name("a.msdx").build();
        when(contentService.getPage(MEMBER_ID, req)).thenReturn(new PageResult<>(List.of(content), 1L));
        when(memberUserMapper.selectById(MEMBER_ID)).thenReturn(
                MemberUserDO.builder().id(MEMBER_ID).nickname("张三").build());
        when(contentService.canDelete(MEMBER_ID, content)).thenReturn(true);

        AppShareContentRespVO vo = controller.getPage(req).getData().getList().get(0);
        assertEquals(MEMBER_ID, vo.getUploaderMemberId());
        assertEquals("张三", vo.getUploaderNickname());
        assertTrue(vo.getCanDelete());
    }

    @Test
    void enterprisePagePassesScopeAndFolder() {
        AppShareContentPageReqVO req = new AppShareContentPageReqVO();
        req.setScope(2);
        req.setFolderId(3L);
        when(contentService.getPage(eq(MEMBER_ID), any())).thenReturn(new PageResult<>(List.of(), 0L));

        controller.getPage(req);

        org.mockito.ArgumentCaptor<AppShareContentPageReqVO> captor =
                org.mockito.ArgumentCaptor.forClass(AppShareContentPageReqVO.class);
        org.mockito.Mockito.verify(contentService).getPage(eq(MEMBER_ID), captor.capture());
        assertEquals(2, captor.getValue().getScope());
        assertEquals(3L, captor.getValue().getFolderId());
    }

    @Test
    void mineUsesLoggedInMember() {
        when(contentService.getMyEnterprisePage(eq(MEMBER_ID), any())).thenReturn(new PageResult<>(List.of(), 0L));
        assertEquals(0, controller.getMine(new cn.iocoder.yudao.framework.common.pojo.PageParam()).getData().getTotal());
        org.mockito.Mockito.verify(contentService).getMyEnterprisePage(eq(MEMBER_ID), any());
    }

    @Test
    void getMapsDownloadUrl() {
        MaxLabelShareContentDO content = MaxLabelShareContentDO.builder()
                .id(9L).scope(1).ownerMemberId(MEMBER_ID).contentType(5).name("a.png")
                .storageType(2).fileUrl("http://file/a.png").build();
        when(contentService.getContent(MEMBER_ID, 9L)).thenReturn(content);
        when(memberUserMapper.selectById(MEMBER_ID)).thenReturn(
                MemberUserDO.builder().id(MEMBER_ID).nickname("张三").build());
        when(contentService.canDelete(MEMBER_ID, content)).thenReturn(true);
        when(contentService.getDownloadUrl(content)).thenReturn("http://signed/a.png");

        assertEquals("http://signed/a.png", controller.get(9L).getData().getDownloadUrl());
    }

}
