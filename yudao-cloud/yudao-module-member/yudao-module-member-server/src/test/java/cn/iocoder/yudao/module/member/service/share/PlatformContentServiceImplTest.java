package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentStatusReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentUploadReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelPlatformContentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_PLATFORM_CONTENT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlatformContentServiceImplTest {

    @Mock private MaxLabelPlatformContentMapper platformContentMapper;
    @Mock private ShareFileStorage fileStorage;
    @Mock private ShareProperties properties;

    @InjectMocks private PlatformContentServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getContentMaxBytes()).thenReturn(16L * 1024 * 1024);
    }

    @Test
    void uploadTemplateStripsDatasetRowsAndPublishes() throws Exception {
        String json = "{\"doc\":{\"datasets\":{\"d\":{\"rows\":[[\"x\"]]}},\"objects\":[]}}";
        MockMultipartFile file = new MockMultipartFile("file", "a.msdx", "application/json",
                json.getBytes(StandardCharsets.UTF_8));
        AdminPlatformContentUploadReqVO req = new AdminPlatformContentUploadReqVO();
        req.setContentType(1);
        req.setName("a.msdx");
        req.setFile(file);
        doAnswer(invocation -> {
            MaxLabelPlatformContentDO row = invocation.getArgument(0);
            row.setId(8L);
            return 1;
        }).when(platformContentMapper).insert(any(MaxLabelPlatformContentDO.class));

        assertEquals(8L, service.upload(7L, req));

        ArgumentCaptor<MaxLabelPlatformContentDO> captor = ArgumentCaptor.forClass(MaxLabelPlatformContentDO.class);
        verify(platformContentMapper).insert(captor.capture());
        MaxLabelPlatformContentDO saved = captor.getValue();
        assertFalse(saved.getContent().contains("rows"));
        assertEquals(1, saved.getStatus());
        assertEquals(7L, saved.getPublisherUserId());
        assertNotNull(saved.getPublishedTime());
    }

    @Test
    void uploadExcelInfersTypeFromExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2});
        AdminPlatformContentUploadReqVO req = new AdminPlatformContentUploadReqVO();
        req.setContentType(1);
        req.setName("a.xlsx");
        req.setFile(file);
        when(fileStorage.store(any(), eq("a.xlsx"), eq(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .thenReturn("http://file/a.xlsx");
        doAnswer(invocation -> {
            MaxLabelPlatformContentDO row = invocation.getArgument(0);
            row.setId(9L);
            return 1;
        }).when(platformContentMapper).insert(any(MaxLabelPlatformContentDO.class));

        assertEquals(9L, service.upload(7L, req));

        ArgumentCaptor<MaxLabelPlatformContentDO> captor =
                ArgumentCaptor.forClass(MaxLabelPlatformContentDO.class);
        verify(platformContentMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getContentType());
        assertEquals(2, captor.getValue().getStorageType());
    }

    @Test
    void offlineContentIsHidden() {
        when(platformContentMapper.selectById(8L)).thenReturn(MaxLabelPlatformContentDO.builder()
                .id(8L).status(0).name("a.msdx").build());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getOnline(8L));
        assertEquals(MEMBER_PLATFORM_CONTENT_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void onlineStatusSetsPublishedTime() {
        when(platformContentMapper.selectById(8L)).thenReturn(MaxLabelPlatformContentDO.builder()
                .id(8L).status(0).name("a.msdx").build());
        AdminPlatformContentStatusReqVO req = new AdminPlatformContentStatusReqVO();
        req.setId(8L);
        req.setStatus(1);

        service.updateStatus(req);

        ArgumentCaptor<MaxLabelPlatformContentDO> captor = ArgumentCaptor.forClass(MaxLabelPlatformContentDO.class);
        verify(platformContentMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getPublishedTime());
    }

}
