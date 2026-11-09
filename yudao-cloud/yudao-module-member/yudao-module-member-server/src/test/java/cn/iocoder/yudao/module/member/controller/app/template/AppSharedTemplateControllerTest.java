package cn.iocoder.yudao.module.member.controller.app.template;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplateContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplateRespVO;
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
public class AppSharedTemplateControllerTest {

    @Mock
    private PlatformContentService platformContentService;
    @InjectMocks
    private AppSharedTemplateController controller;

    @Test
    void pageMapsPlatformPublisher() {
        AppSharedTemplatePageReqVO req = new AppSharedTemplatePageReqVO();
        MaxLabelPlatformContentDO content = MaxLabelPlatformContentDO.builder()
                .id(7L).contentType(1).name("共享面单.msdx").build();
        when(platformContentService.getOnlinePage(any())).thenReturn(
                new PageResult<>(List.of(content), 1L));

        AppSharedTemplateRespVO vo = controller.getPage(req).getData().getList().get(0);
        assertEquals("共享面单.msdx", vo.getName());
        assertEquals("平台运营", vo.getPublisherNickname());
        assertEquals(1, vo.getContentType());
    }

    @Test
    void getReturnsPlatformContent() {
        when(platformContentService.getOnline(7L)).thenReturn(MaxLabelPlatformContentDO.builder()
                .id(7L).contentType(1).name("共享面单.msdx").content("{\"x\":1}").build());

        AppSharedTemplateContentRespVO vo = controller.get(7L).getData();
        assertEquals("共享面单.msdx", vo.getName());
        assertEquals("{\"x\":1}", vo.getContent());
    }

}
