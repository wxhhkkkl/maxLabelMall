package cn.iocoder.yudao.module.member.controller.admin.share;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminOrgShareContentPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.share.ShareContentService;
import cn.iocoder.yudao.module.member.service.share.ShareFolderService;
import cn.iocoder.yudao.module.member.service.share.ShareSpaceService;
import cn.iocoder.yudao.module.member.service.share.dto.ShareFolderInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminOrgShareContentControllerTest {

    @Mock private ShareContentService contentService;
    @Mock private ShareFolderService folderService;
    @Mock private ShareSpaceService spaceService;
    @Mock private MemberUserMapper memberUserMapper;
    @InjectMocks private AdminOrgShareContentController controller;

    @Test
    void overviewUsesEnterpriseQuota() {
        when(spaceService.getEnterpriseUsedBytes(10L)).thenReturn(1024L);
        var resp = controller.getOverview(10L).getData();
        assertEquals(300L * 1024 * 1024, resp.getQuotaBytes());
        assertEquals(1024L, resp.getUsedBytes());
        assertEquals(300L * 1024 * 1024 - 1024L, resp.getRemainingBytes());
    }

    @Test
    void pageMapsFolderAndUploader() {
        AdminOrgShareContentPageReqVO req = new AdminOrgShareContentPageReqVO();
        req.setOrgId(10L);
        MaxLabelShareContentDO content = MaxLabelShareContentDO.builder()
                .id(9L).scope(2).orgId(10L).folderId(3L).ownerMemberId(1001L)
                .contentType(1).name("a.msdx").build();
        when(contentService.getOrgPageForAdmin(eq(10L), any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(content), 1L));
        ShareFolderInfo folder = new ShareFolderInfo();
        folder.setId(3L);
        folder.setName("财务");
        when(folderService.getOrgListForAdmin(10L)).thenReturn(List.of(folder));
        when(memberUserMapper.selectById(1001L)).thenReturn(
                MemberUserDO.builder().id(1001L).nickname("张三").build());

        var vo = controller.getPage(req).getData().getList().get(0);
        assertEquals("财务", vo.getFolderName());
        assertEquals("张三", vo.getUploaderNickname());
    }

    @Test
    void getReturnsContentAndDownloadUrl() {
        MaxLabelShareContentDO content = MaxLabelShareContentDO.builder()
                .id(9L).scope(2).orgId(10L).folderId(3L).ownerMemberId(1001L)
                .contentType(1).name("a.msdx").content("{\"x\":1}").storageType(1).build();
        when(contentService.getContentForAdmin(9L)).thenReturn(content);
        when(memberUserMapper.selectById(1001L)).thenReturn(
                MemberUserDO.builder().id(1001L).nickname("张三").build());
        when(contentService.getDownloadUrl(content)).thenReturn("http://signed/a.msdx");

        var vo = controller.get(9L).getData();
        assertEquals("{\"x\":1}", vo.getContent());
        assertEquals("http://signed/a.msdx", vo.getDownloadUrl());
    }

    @Test
    void permissionRequiresOrgQuery() throws Exception {
        for (String method : List.of("getOverview", "getFolders", "getPage", "get")) {
            var target = switch (method) {
                case "getOverview", "getFolders" -> AdminOrgShareContentController.class.getMethod(method, Long.class);
                case "getPage" -> AdminOrgShareContentController.class.getMethod(method, AdminOrgShareContentPageReqVO.class);
                default -> AdminOrgShareContentController.class.getMethod(method, Long.class);
            };
            var annotation = target.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            assertEquals("@ss.hasPermission('member:org:query')", annotation.value(), method);
        }
    }

}
