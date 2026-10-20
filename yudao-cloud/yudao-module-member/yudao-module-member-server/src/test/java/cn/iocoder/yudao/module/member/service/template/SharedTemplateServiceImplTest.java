package cn.iocoder.yudao.module.member.service.template;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import cn.iocoder.yudao.module.member.dal.mysql.template.MaxLabelSharedTemplateMapper;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SharedTemplateServiceImplTest {

    @Mock
    private MaxLabelSharedTemplateMapper sharedTemplateMapper;
    @Mock
    private MemberSubscriptionService subscriptionService;
    @InjectMocks
    private SharedTemplateServiceImpl sharedTemplateService;

    private static final Long MEMBER_ID = 1001L;

    @BeforeEach
    void activeSubscription() {
        AppMemberSubscriptionRespVO subscription = new AppMemberSubscriptionRespVO();
        subscription.setActive(true);
        lenient().when(subscriptionService.getCurrent(MEMBER_ID)).thenReturn(subscription);
    }

    private MaxLabelLabelTemplateDO template() {
        return MaxLabelLabelTemplateDO.builder()
                .id(9L).memberId(MEMBER_ID).name("面单")
                .content("{\"doc\":{\"connections\":{\"db\":{\"password\":\"p\",\"host\":\"h\"}},"
                        + "\"objects\":[{\"type\":\"rfid\",\"accessPwd\":\"a\",\"killPwd\":\"k\"}],"
                        + "\"datasets\":{\"d\":{\"columns\":[\"c\"],\"rows\":[[\"secret\"]]}}}}")
                .widthMm(java.math.BigDecimal.TEN)
                .heightMm(java.math.BigDecimal.ONE)
                .build();
    }

    @Test
    void submitCreatesSanitizedPendingCopy() {
        when(sharedTemplateMapper.selectBySourceTemplateId(9L)).thenReturn(null);
        when(sharedTemplateMapper.insert(any(MaxLabelSharedTemplateDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MaxLabelSharedTemplateDO.class).setId(7L);
            return 1;
        });

        sharedTemplateService.submit(MEMBER_ID, template(), "物流", "面单");

        ArgumentCaptor<MaxLabelSharedTemplateDO> captor = ArgumentCaptor.forClass(MaxLabelSharedTemplateDO.class);
        verify(sharedTemplateMapper).insert(captor.capture());
        MaxLabelSharedTemplateDO saved = captor.getValue();
        assertEquals(MaxLabelSharedTemplateDO.AUDIT_PENDING, saved.getAuditStatus());
        assertFalse(saved.getContent().contains("password"));
        assertFalse(saved.getContent().contains("accessPwd"));
        assertFalse(saved.getContent().contains("killPwd"));
        assertFalse(saved.getContent().contains("secret"));
        assertTrue(saved.getContent().contains("columns"), "共享副本保留列定义");
    }

    @Test
    void approveOnlyFromPending() {
        MaxLabelSharedTemplateDO pending = MaxLabelSharedTemplateDO.builder()
                .id(7L).auditStatus(MaxLabelSharedTemplateDO.AUDIT_PENDING).build();
        when(sharedTemplateMapper.selectById(7L)).thenReturn(pending);

        sharedTemplateService.approve(7L, 1L);

        assertEquals(MaxLabelSharedTemplateDO.AUDIT_APPROVED, pending.getAuditStatus());
        assertEquals(1L, pending.getAuditUserId());
        assertNotNull(pending.getAuditTime());
        assertNotNull(pending.getPublishedTime());
        verify(sharedTemplateMapper).updateById(pending);
    }

    @Test
    void offlineRejectsPending() {
        when(sharedTemplateMapper.selectById(7L)).thenReturn(
                MaxLabelSharedTemplateDO.builder().id(7L)
                        .auditStatus(MaxLabelSharedTemplateDO.AUDIT_PENDING).build());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> sharedTemplateService.offline(7L, 1L));
        assertEquals(MEMBER_SHARED_TEMPLATE_INVALID_STATUS.getCode(), exception.getCode());
    }

    @Test
    void cancelRejectsAnotherPublisher() {
        when(sharedTemplateMapper.selectBySourceTemplateId(9L)).thenReturn(
                MaxLabelSharedTemplateDO.builder().id(7L).sourceTemplateId(9L)
                        .publisherMemberId(2002L).build());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> sharedTemplateService.cancel(MEMBER_ID, 9L));
        assertEquals(MEMBER_SHARED_TEMPLATE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void publicContentOnlyReturnsApproved() {
        when(sharedTemplateMapper.selectById(7L)).thenReturn(
                MaxLabelSharedTemplateDO.builder().id(7L)
                        .auditStatus(MaxLabelSharedTemplateDO.AUDIT_PENDING).build());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> sharedTemplateService.getPublicContent(7L));
        assertEquals(MEMBER_SHARED_TEMPLATE_NOT_FOUND.getCode(), exception.getCode());
    }

}
