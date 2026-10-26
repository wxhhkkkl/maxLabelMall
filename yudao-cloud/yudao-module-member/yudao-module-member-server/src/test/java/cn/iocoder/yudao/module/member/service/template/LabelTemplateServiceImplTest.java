package cn.iocoder.yudao.module.member.service.template;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateSaveReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import cn.iocoder.yudao.module.member.dal.mysql.template.MaxLabelLabelTemplateMapper;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
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
public class LabelTemplateServiceImplTest {

    @Mock
    private MaxLabelLabelTemplateMapper templateMapper;
    @Mock
    private SharedTemplateService sharedTemplateService;
    @Mock
    private MemberSubscriptionService subscriptionService;
    @InjectMocks
    private LabelTemplateServiceImpl templateService;

    private static final Long MEMBER_ID = 1001L;

    @BeforeEach
    void activeSubscription() {
        AppMemberSubscriptionRespVO subscription = new AppMemberSubscriptionRespVO();
        subscription.setActive(true);
        lenient().when(subscriptionService.getCurrent(MEMBER_ID)).thenReturn(subscription);
    }

    private AppLabelTemplateSaveReqVO request(String content) {
        AppLabelTemplateSaveReqVO req = new AppLabelTemplateSaveReqVO();
        req.setName("面单");
        req.setContent(content);
        return req;
    }

    @Test
    void createStripsSecretsAndComputesSizeAndHash() {
        when(templateMapper.selectListByMemberId(MEMBER_ID)).thenReturn(List.of());
        when(templateMapper.insert(any(MaxLabelLabelTemplateDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MaxLabelLabelTemplateDO.class).setId(9L);
            return 1;
        });
        String content = "{\"format\":\"maxlabel-msdx\",\"doc\":{\"connections\":{\"db\":{\"password\":\"p\",\"host\":\"h\"}},"
                + "\"objects\":[{\"type\":\"rfid\",\"accessPwd\":\"a\",\"killPwd\":\"k\"}],\"datasets\":{\"d\":{\"rows\":[[\"x\"]]}}}}";

        assertEquals(9L, templateService.create(MEMBER_ID, request(content)));
        ArgumentCaptor<MaxLabelLabelTemplateDO> captor = ArgumentCaptor.forClass(MaxLabelLabelTemplateDO.class);
        verify(templateMapper).insert(captor.capture());
        MaxLabelLabelTemplateDO saved = captor.getValue();
        assertFalse(saved.getContent().contains("password"));
        assertFalse(saved.getContent().contains("accessPwd"));
        assertFalse(saved.getContent().contains("killPwd"));
        assertTrue(saved.getContent().contains("rows"), "私有模板保留数据集行");
        assertTrue(saved.getSizeBytes() > 0);
        assertEquals(64, saved.getContentHash().length());
    }

    @Test
    void rejectsContentOverSixteenMegabytes() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> templateService.create(MEMBER_ID, request("x".repeat(16 * 1024 * 1024 + 1))));
        assertEquals(MEMBER_TEMPLATE_TOO_LARGE.getCode(), exception.getCode());
    }

    @Test
    void rejectsOverQuota() {
        when(templateMapper.selectListByMemberId(MEMBER_ID)).thenReturn(
                java.util.Collections.nCopies(1000, MaxLabelLabelTemplateDO.builder().sizeBytes(1).build()));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> templateService.create(MEMBER_ID, request("{}")));
        assertEquals(MEMBER_TEMPLATE_QUOTA_EXCEEDED.getCode(), exception.getCode());
    }

    @Test
    void deleteDoesNotRequireActiveSubscription() {
        when(templateMapper.selectByIdAndMemberId(9L, MEMBER_ID)).thenReturn(
                MaxLabelLabelTemplateDO.builder().id(9L).memberId(MEMBER_ID).build());
        templateService.delete(MEMBER_ID, 9L);
        verify(sharedTemplateService).deleteBySource(9L);
        verify(templateMapper).deleteById(9L);
        verify(subscriptionService, never()).getCurrent(any());
    }

    @Test
    void nonOwnerCannotRead() {
        when(templateMapper.selectByIdAndMemberId(9L, MEMBER_ID)).thenReturn(null);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> templateService.getContent(MEMBER_ID, 9L));
        assertEquals(MEMBER_TEMPLATE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void enterpriseSeatAllowsCreate() {
        AppMemberSubscriptionRespVO subscription = new AppMemberSubscriptionRespVO();
        subscription.setActive(true);
        subscription.setSourceType(2);
        subscription.setOrgName("测试企业");
        when(subscriptionService.getCurrent(MEMBER_ID)).thenReturn(subscription);
        when(templateMapper.selectListByMemberId(MEMBER_ID)).thenReturn(List.of());
        when(templateMapper.insert(any(MaxLabelLabelTemplateDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MaxLabelLabelTemplateDO.class).setId(10L);
            return 1;
        });

        assertEquals(10L, templateService.create(MEMBER_ID, request("{}")));
    }

    @Test
    void expiredEnterpriseSeatRejectsCreate() {
        AppMemberSubscriptionRespVO subscription = new AppMemberSubscriptionRespVO();
        subscription.setActive(false);
        when(subscriptionService.getCurrent(MEMBER_ID)).thenReturn(subscription);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> templateService.create(MEMBER_ID, request("{}")));
        assertEquals(MEMBER_SUBSCRIPTION_REQUIRED.getCode(), exception.getCode());
    }

}
