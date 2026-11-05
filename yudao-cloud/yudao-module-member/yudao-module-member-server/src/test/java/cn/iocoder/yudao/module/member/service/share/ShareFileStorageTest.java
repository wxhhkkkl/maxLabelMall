package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ShareFileStorageTest {

    @Mock
    private FileApi fileApi;
    @InjectMocks
    private ShareFileStorage fileStorage;

    @Test
    void storeDelegatesToFileApi() {
        when(fileApi.createFile(any(), eq("a.png"), eq("maxlabel-share"), eq("image/png")))
                .thenReturn("http://file/a.png");
        assertEquals("http://file/a.png",
                fileStorage.store(new byte[]{1}, "a.png", "image/png"));
    }

    @Test
    void presignReturnsShortUrl() {
        when(fileApi.presignGetUrl("http://file/a.png", 300))
                .thenReturn(CommonResult.success("http://signed/a.png"));
        assertEquals("http://signed/a.png", fileStorage.presign("http://file/a.png"));
    }

    @Test
    void presignFallsBackToOriginalOnFailure() {
        when(fileApi.presignGetUrl("http://file/a.png", 300))
                .thenThrow(new RuntimeException("no presign"));
        assertEquals("http://file/a.png", fileStorage.presign("http://file/a.png"));
    }

}
