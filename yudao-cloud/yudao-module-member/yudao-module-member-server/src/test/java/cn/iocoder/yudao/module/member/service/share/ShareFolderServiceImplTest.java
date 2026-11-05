package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareFolderDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareFolderMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareFolderMemberMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgMemberService;
import cn.iocoder.yudao.module.member.service.share.dto.ShareEnterpriseAccess;
import cn.iocoder.yudao.module.member.service.share.dto.ShareFolderInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShareFolderServiceImplTest {

    private static final Long MEMBER_ID = 1001L;
    private static final Long ORG_ID = 2001L;

    @Mock private ShareSpaceService spaceService;
    @Mock private MaxLabelOrgMemberService orgMemberService;
    @Mock private MaxLabelShareFolderMapper folderMapper;
    @Mock private MaxLabelShareFolderMemberMapper folderMemberMapper;
    @Mock private MaxLabelShareContentMapper contentMapper;
    @Mock private MemberUserMapper memberUserMapper;

    @InjectMocks private ShareFolderServiceImpl folderService;

    @Test
    void adminCanCreateWhenLicenseActive() {
        when(spaceService.getEnterpriseAccess(MEMBER_ID)).thenReturn(access(true, true));
        doAnswer(invocation -> {
            MaxLabelShareFolderDO folder = invocation.getArgument(0);
            folder.setId(5L);
            return 1;
        }).when(folderMapper).insert(any(MaxLabelShareFolderDO.class));

        assertEquals(5L, folderService.create(MEMBER_ID, "财务"));
        ArgumentCaptor<MaxLabelShareFolderDO> captor = ArgumentCaptor.forClass(MaxLabelShareFolderDO.class);
        verify(folderMapper).insert(captor.capture());
        assertEquals(ORG_ID, captor.getValue().getOrgId());
    }

    @Test
    void nonAdminCannotCreate() {
        when(spaceService.getEnterpriseAccess(MEMBER_ID)).thenReturn(access(false, true));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> folderService.create(MEMBER_ID, "财务"));
        assertEquals(MEMBER_ORG_NOT_ADMIN.getCode(), exception.getCode());
    }

    @Test
    void expiredLicenseCannotCreate() {
        when(spaceService.getEnterpriseAccess(MEMBER_ID)).thenReturn(access(true, false));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> folderService.create(MEMBER_ID, "财务"));
        assertEquals(MEMBER_SHARE_ENTERPRISE_READ_ONLY.getCode(), exception.getCode());
    }

    @Test
    void nonEmptyFolderCannotBeDeleted() {
        when(spaceService.getEnterpriseAccess(MEMBER_ID)).thenReturn(access(true, true));
        when(folderMapper.selectByIdAndOrgId(5L, ORG_ID)).thenReturn(folder(5L));
        when(contentMapper.countByFolder(5L)).thenReturn(1);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> folderService.delete(MEMBER_ID, 5L));
        assertEquals(MEMBER_SHARE_FOLDER_NOT_EMPTY.getCode(), exception.getCode());
        verify(folderMapper, never()).deleteById(any());
    }

    @Test
    void memberOnlySeesVisibleFolders() {
        when(spaceService.getEnterpriseAccess(MEMBER_ID)).thenReturn(access(false, true));
        when(folderMapper.selectListByOrgId(ORG_ID)).thenReturn(List.of(folder(1L), folder(2L)));
        when(folderMemberMapper.selectVisibleFolderIds(MEMBER_ID, List.of(1L, 2L))).thenReturn(List.of(1L));
        when(folderMemberMapper.selectMemberIdsByFolderId(1L)).thenReturn(List.of());

        List<ShareFolderInfo> list = folderService.getVisibleList(MEMBER_ID);
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).getId());
        assertNull(list.get(0).getContentCount());
    }

    @Test
    void memberListRejectsMemberOutsideOrg() {
        when(spaceService.getEnterpriseAccess(MEMBER_ID)).thenReturn(access(true, true));
        when(folderMapper.selectByIdAndOrgId(5L, ORG_ID)).thenReturn(folder(5L));
        when(orgMemberService.getMemberList(ORG_ID)).thenReturn(List.of(
                MaxLabelOrgMemberDO.builder().orgId(ORG_ID).memberId(1L).role(3).build()));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> folderService.updateMembers(MEMBER_ID, 5L, List.of(2L)));
        assertEquals(MEMBER_SHARE_FOLDER_MEMBER_INVALID.getCode(), exception.getCode());
    }

    private ShareEnterpriseAccess access(boolean admin, boolean license) {
        ShareEnterpriseAccess access = new ShareEnterpriseAccess();
        access.setOrgId(ORG_ID);
        access.setOrgName("赋签");
        access.setOrgStatus(0);
        access.setSeatActive(true);
        access.setLicenseActive(license);
        access.setIsAdmin(admin);
        return access;
    }

    private MaxLabelShareFolderDO folder(Long id) {
        return MaxLabelShareFolderDO.builder().id(id).orgId(ORG_ID).name("夹" + id).build();
    }

}
