package cn.iocoder.yudao.module.member.controller.app.share;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppPlatformContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppPlatformContentRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentDO;
import cn.iocoder.yudao.module.member.service.share.PlatformContentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppPlatformContentControllerTest {

    @Mock private PlatformContentService platformContentService;
    @InjectMocks private AppPlatformContentController controller;

    @Test
    void pageMapsContentType() {
        AppPlatformContentPageReqVO req = new AppPlatformContentPageReqVO();
        MaxLabelPlatformContentDO content = MaxLabelPlatformContentDO.builder()
                .id(8L).contentType(5).name("a.png").build();
        when(platformContentService.getOnlinePage(any())).thenReturn(
                new PageResult<>(List.of(content), 1L));

        AppPlatformContentRespVO vo = controller.getPage(req).getData().getList().get(0);
        assertEquals("a.png", vo.getName());
        assertEquals(5, vo.getContentType());
    }

}
