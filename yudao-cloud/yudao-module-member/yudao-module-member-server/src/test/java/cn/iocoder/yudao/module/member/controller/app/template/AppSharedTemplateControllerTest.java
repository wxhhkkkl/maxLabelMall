package cn.iocoder.yudao.module.member.controller.app.template;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplateRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.template.SharedTemplateService;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppSharedTemplateControllerTest {

    @Mock
    private SharedTemplateService sharedTemplateService;
    @Mock
    private MemberUserService memberUserService;
    @InjectMocks
    private AppSharedTemplateController controller;

    @Test
    void pageMapsPublisherNickname() {
        AppSharedTemplatePageReqVO req = new AppSharedTemplatePageReqVO();
        MaxLabelSharedTemplateDO shared = MaxLabelSharedTemplateDO.builder()
                .id(7L).publisherMemberId(1001L).name("共享面单").build();
        PageResult<MaxLabelSharedTemplateDO> page = new PageResult<>(List.of(shared), 1L);
        when(sharedTemplateService.getPublicPage(req)).thenReturn(page);
        when(memberUserService.getUser(1001L)).thenReturn(MemberUserDO.builder()
                .id(1001L).nickname("张三").mobile("13800138000").build());

        AppSharedTemplateRespVO vo = controller.getPage(req).getData().getList().get(0);
        assertEquals("共享面单", vo.getName());
        assertEquals("张三", vo.getPublisherNickname());
    }

}
