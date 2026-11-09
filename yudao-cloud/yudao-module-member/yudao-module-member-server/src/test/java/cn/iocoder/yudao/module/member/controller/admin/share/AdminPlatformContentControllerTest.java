package cn.iocoder.yudao.module.member.controller.admin.share;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentCategorySaveReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentCategoryDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentDO;
import cn.iocoder.yudao.module.member.service.share.PlatformContentCategoryService;
import cn.iocoder.yudao.module.member.service.share.PlatformContentService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminPlatformContentControllerTest {

    @Mock private PlatformContentService platformContentService;
    @Mock private PlatformContentCategoryService categoryService;
    @InjectMocks private AdminPlatformContentController controller;

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
    void pageMapsStatus() {
        AdminPlatformContentPageReqVO req = new AdminPlatformContentPageReqVO();
        MaxLabelPlatformContentDO content = MaxLabelPlatformContentDO.builder()
                .id(8L).contentType(1).name("a.msdx").status(1).build();
        when(platformContentService.getAdminPage(any())).thenReturn(
                new PageResult<>(List.of(content), 1L));

        AdminPlatformContentRespVO vo = controller.getPage(req).getData().getList().get(0);
        assertEquals(1, vo.getStatus());
        assertEquals("a.msdx", vo.getName());
    }

    @Test
    void getReturnsContentAndDownloadUrl() {
        MaxLabelPlatformContentDO content = MaxLabelPlatformContentDO.builder()
                .id(8L).contentType(1).name("a.msdx").content("{\"x\":1}")
                .storageType(1).sizeBytes(7L).build();
        when(platformContentService.getAdmin(8L)).thenReturn(content);
        when(platformContentService.getDownloadUrl(content)).thenReturn("http://signed/a.msdx");

        var vo = controller.get(8L).getData();
        assertEquals("{\"x\":1}", vo.getContent());
        assertEquals("http://signed/a.msdx", vo.getDownloadUrl());
    }

    @Test
    void categoryListAndCreateDelegate() {
        when(categoryService.getList()).thenReturn(List.of(
                MaxLabelPlatformContentCategoryDO.builder().id(3L).name("物流").sort(0).build()));
        assertEquals("物流", controller.getCategoryList().getData().get(0).getName());

        AdminPlatformContentCategorySaveReqVO req = new AdminPlatformContentCategorySaveReqVO();
        req.setName("物流");
        when(categoryService.create("物流", null)).thenReturn(3L);
        assertEquals(3L, controller.createCategory(req).getData());
    }

    @Test
    void deleteDelegates() {
        assertTrue(controller.delete(8L).getData());
        verify(platformContentService).delete(8L);
    }

    @Test
    void permissionAnnotationsArePresent() throws Exception {
        assertPermission("getPage", cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentPageReqVO.class,
                "@ss.hasPermission('member:platform-content:query')");
        assertPermission("get", Long.class,
                "@ss.hasPermission('member:platform-content:query')");
        assertPermission("upload", cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentUploadReqVO.class,
                "@ss.hasPermission('member:platform-content:create')");
        assertPermission("create", cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentSaveReqVO.class,
                "@ss.hasPermission('member:platform-content:create')");
        assertPermission("update", cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentSaveReqVO.class,
                "@ss.hasPermission('member:platform-content:update')");
        assertPermission("updateStatus", cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentStatusReqVO.class,
                "@ss.hasPermission('member:platform-content:update')");
        assertPermission("delete", Long.class,
                "@ss.hasPermission('member:platform-content:delete')");
        assertPermission("getCategoryList", null,
                "@ss.hasPermission('member:platform-content:query')");
        assertPermission("createCategory", AdminPlatformContentCategorySaveReqVO.class,
                "@ss.hasPermission('member:platform-content:update')");
        assertPermission("deleteCategory", Long.class,
                "@ss.hasPermission('member:platform-content:update')");
    }

    private void assertPermission(String method, Class<?> parameter, String expected) throws Exception {
        java.lang.reflect.Method target = parameter == null
                ? AdminPlatformContentController.class.getMethod(method)
                : AdminPlatformContentController.class.getMethod(method, parameter);
        org.springframework.security.access.prepost.PreAuthorize annotation =
                target.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
        org.junit.jupiter.api.Assertions.assertNotNull(annotation, method);
        assertEquals(expected, annotation.value(), method);
    }

}
