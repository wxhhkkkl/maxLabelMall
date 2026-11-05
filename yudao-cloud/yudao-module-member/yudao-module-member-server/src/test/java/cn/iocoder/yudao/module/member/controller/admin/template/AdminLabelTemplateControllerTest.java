package cn.iocoder.yudao.module.member.controller.admin.template;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminLabelTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateContentRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.service.share.ShareContentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminLabelTemplateControllerTest {

    @Mock private ShareContentService contentService;
    @Mock private MaxLabelShareContentMapper contentMapper;
    @InjectMocks private AdminLabelTemplateController controller;

    @Test
    void pageReadsNewTableAndClearsShareStatus() {
        AdminLabelTemplatePageReqVO req = new AdminLabelTemplatePageReqVO();
        req.setUserId(1001L);
        MaxLabelShareContentDO content = MaxLabelShareContentDO.builder()
                .id(9L).scope(1).ownerMemberId(1001L).contentType(1).name("a.msdx").build();
        when(contentService.getPage(any(), any())).thenReturn(new PageResult<>(List.of(content), 1L));

        AppLabelTemplateRespVO vo = controller.getPage(req).getData().getList().get(0);
        assertEquals("a.msdx", vo.getName());
        assertNull(vo.getShareStatus());
    }

    @Test
    void getReadsNewTableContent() {
        when(contentMapper.selectById(9L)).thenReturn(MaxLabelShareContentDO.builder()
                .id(9L).scope(1).contentType(1).name("a.msdx").content("{\"x\":1}").build());

        AppLabelTemplateContentRespVO vo = controller.get(9L).getData();
        assertEquals("a.msdx", vo.getName());
        assertEquals("{\"x\":1}", vo.getContent());
    }

}
