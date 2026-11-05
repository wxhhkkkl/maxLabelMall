package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentSaveReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentUploadReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.service.share.dto.ShareEnterpriseAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShareContentServiceImplTest {

    private static final Long MEMBER_ID = 1001L;
    private static final Long ORG_ID = 2001L;
    private static final long TEN_MB = 10L * 1024 * 1024;
    private static final long SIXTEEN_MB = 16L * 1024 * 1024;

    @Mock private ShareSpaceService spaceService;
    @Mock private ShareFolderService folderService;
    @Mock private ShareFileStorage fileStorage;
    @Mock private ShareProperties properties;
    @Mock private MaxLabelShareContentMapper contentMapper;

    @InjectMocks private ShareContentServiceImpl contentService;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getContentMaxBytes()).thenReturn(SIXTEEN_MB);
        lenient().when(spaceService.getPersonalQuotaBytes(MEMBER_ID)).thenReturn(TEN_MB);
        lenient().when(spaceService.getPersonalUsedBytes(MEMBER_ID)).thenReturn(0L);
        lenient().doAnswer(invocation -> {
            MaxLabelShareContentDO row = invocation.getArgument(0);
            row.setId(9L);
            return 1;
        }).when(contentMapper).insert(any(MaxLabelShareContentDO.class));
    }

    @Test
    void personalCreateSanitizesTemplateAndStoresInline() {
        AppShareContentSaveReqVO req = new AppShareContentSaveReqVO();
        req.setScope(1);
        req.setContentType(1);
        req.setName("面单.msdx");
        req.setContent("{\"doc\":{\"widthMm\":100,\"heightMm\":150,\"connections\":{\"db\":{\"password\":\"p\",\"host\":\"h\"}},\"objects\":[]}}");

        assertEquals(9L, contentService.create(MEMBER_ID, req));

        ArgumentCaptor<MaxLabelShareContentDO> captor = ArgumentCaptor.forClass(MaxLabelShareContentDO.class);
        verify(contentMapper).insert(captor.capture());
        MaxLabelShareContentDO saved = captor.getValue();
        assertEquals(1, saved.getScope());
        assertEquals(MEMBER_ID, saved.getOwnerMemberId());
        assertEquals(1, saved.getStorageType());
        assertFalse(saved.getContent().contains("password"));
        assertTrue(saved.getSizeBytes() > 0);
        assertEquals(0, new java.math.BigDecimal("100").compareTo(saved.getWidthMm()));
        assertEquals(0, new java.math.BigDecimal("150").compareTo(saved.getHeightMm()));
        verify(spaceService).lockSpace(1, MEMBER_ID);
        verify(spaceService).refreshUsedBytes(1, MEMBER_ID);
    }

    @Test
    void personalCreateRejectsWhenQuotaExceeded() {
        when(spaceService.getPersonalUsedBytes(MEMBER_ID)).thenReturn(TEN_MB);
        AppShareContentSaveReqVO req = new AppShareContentSaveReqVO();
        req.setScope(1);
        req.setContentType(2);
        req.setName("a.txt");
        req.setContent("x");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> contentService.create(MEMBER_ID, req));
        assertEquals(MEMBER_SHARE_QUOTA_EXCEEDED.getCode(), exception.getCode());
        verify(contentMapper, never()).insert(any(MaxLabelShareContentDO.class));
    }

    @Test
    void enterpriseCreateWithoutSeatIsRejected() {
        ShareEnterpriseAccess access = new ShareEnterpriseAccess();
        access.setOrgId(ORG_ID);
        access.setSeatActive(false);
        access.setLicenseActive(true);
        when(spaceService.getEnterpriseAccess(MEMBER_ID)).thenReturn(access);

        AppShareContentSaveReqVO req = new AppShareContentSaveReqVO();
        req.setScope(2);
        req.setFolderId(3L);
        req.setContentType(2);
        req.setName("a.txt");
        req.setContent("x");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> contentService.create(MEMBER_ID, req));
        assertEquals(MEMBER_SHARE_SEAT_REQUIRED.getCode(), exception.getCode());
    }

    @Test
    void deleteOwnEnterpriseContentDoesNotRequireFolderVisibility() {
        MaxLabelShareContentDO content = MaxLabelShareContentDO.builder()
                .id(9L).scope(2).ownerMemberId(MEMBER_ID).orgId(ORG_ID).folderId(3L)
                .contentType(2).name("a.txt").sizeBytes(1L).build();
        ShareEnterpriseAccess access = new ShareEnterpriseAccess();
        access.setOrgId(ORG_ID);
        access.setSeatActive(true);
        access.setLicenseActive(false);
        when(contentMapper.selectById(9L)).thenReturn(content);
        when(spaceService.getEnterpriseAccess(MEMBER_ID)).thenReturn(access);

        contentService.delete(MEMBER_ID, 9L);

        verify(contentMapper).deleteById(9L);
        verify(folderService, never()).requireView(any(), any(), any());
        verify(spaceService).refreshUsedBytes(2, ORG_ID);
    }

    @Test
    void uploadImageStoresFileAndMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png",
                new byte[]{1, 2, 3});
        AppShareContentUploadReqVO req = new AppShareContentUploadReqVO();
        req.setScope(1);
        req.setContentType(5);
        req.setName("a.png");
        req.setFile(file);
        when(fileStorage.store(any(), eq("a.png"), eq("image/png"))).thenReturn("http://file/a.png");

        assertEquals(9L, contentService.upload(MEMBER_ID, req));

        ArgumentCaptor<MaxLabelShareContentDO> captor = ArgumentCaptor.forClass(MaxLabelShareContentDO.class);
        verify(contentMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getStorageType());
        assertEquals("http://file/a.png", captor.getValue().getFileUrl());
        assertEquals(3L, captor.getValue().getSizeBytes());
    }

}
