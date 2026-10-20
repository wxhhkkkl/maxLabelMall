package cn.iocoder.yudao.module.member.service.template;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.template.vo.AdminSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import cn.iocoder.yudao.module.member.dal.mysql.template.MaxLabelSharedTemplateMapper;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

@Service
@Validated
public class SharedTemplateServiceImpl implements SharedTemplateService {

    private static final int MAX_CONTENT_BYTES = 16 * 1024 * 1024;

    @Resource
    private MaxLabelSharedTemplateMapper sharedTemplateMapper;
    @Resource
    private MemberSubscriptionService subscriptionService;

    @Override
    public MaxLabelSharedTemplateDO submit(Long memberId, MaxLabelLabelTemplateDO template,
                                           String category, String keywords) {
        requireActiveSubscription(memberId);
        String content = sanitize(template.getContent());
        int size = content.getBytes(StandardCharsets.UTF_8).length;
        MaxLabelSharedTemplateDO existing = sharedTemplateMapper.selectBySourceTemplateId(template.getId());
        if (existing == null) {
            MaxLabelSharedTemplateDO shared = MaxLabelSharedTemplateDO.builder()
                    .sourceTemplateId(template.getId())
                    .publisherMemberId(memberId)
                    .name(template.getName())
                    .content(content)
                    .sizeBytes(size)
                    .widthMm(template.getWidthMm())
                    .heightMm(template.getHeightMm())
                    .category(category == null ? template.getCategory() : category)
                    .keywords(keywords == null ? template.getKeywords() : keywords)
                    .auditStatus(MaxLabelSharedTemplateDO.AUDIT_PENDING)
                    .build();
            sharedTemplateMapper.insert(shared);
            return shared;
        }
        if (!Objects.equals(existing.getPublisherMemberId(), memberId)) {
            throw exception(MEMBER_SHARED_TEMPLATE_NOT_FOUND);
        }
        existing.setName(template.getName());
        existing.setContent(content);
        existing.setSizeBytes(size);
        existing.setWidthMm(template.getWidthMm());
        existing.setHeightMm(template.getHeightMm());
        if (category != null) existing.setCategory(category);
        if (keywords != null) existing.setKeywords(keywords);
        existing.setAuditStatus(MaxLabelSharedTemplateDO.AUDIT_PENDING);
        existing.setRejectReason(null);
        existing.setAuditUserId(null);
        existing.setAuditTime(null);
        existing.setPublishedTime(null);
        sharedTemplateMapper.updateById(existing);
        return existing;
    }

    @Override
    public void cancel(Long memberId, Long sourceTemplateId) {
        MaxLabelSharedTemplateDO existing = sharedTemplateMapper.selectBySourceTemplateId(sourceTemplateId);
        if (existing == null) return;
        if (!Objects.equals(existing.getPublisherMemberId(), memberId)) {
            throw exception(MEMBER_SHARED_TEMPLATE_NOT_FOUND);
        }
        sharedTemplateMapper.deleteById(existing.getId());
    }

    @Override
    public void deleteBySource(Long sourceTemplateId) {
        MaxLabelSharedTemplateDO existing = sharedTemplateMapper.selectBySourceTemplateId(sourceTemplateId);
        if (existing != null) {
            sharedTemplateMapper.deleteById(existing.getId());
        }
    }

    @Override
    public PageResult<MaxLabelSharedTemplateDO> getPublicPage(AppSharedTemplatePageReqVO reqVO) {
        return sharedTemplateMapper.selectPublicPage(reqVO, reqVO.getKeyword(), reqVO.getCategory(),
                reqVO.getWidthMin(), reqVO.getWidthMax(), reqVO.getHeightMin(), reqVO.getHeightMax());
    }

    @Override
    public MaxLabelSharedTemplateDO getPublicContent(Long id) {
        MaxLabelSharedTemplateDO shared = sharedTemplateMapper.selectById(id);
        if (shared == null || !Objects.equals(shared.getAuditStatus(), MaxLabelSharedTemplateDO.AUDIT_APPROVED)) {
            throw exception(MEMBER_SHARED_TEMPLATE_NOT_FOUND);
        }
        return shared;
    }

    @Override
    public PageResult<MaxLabelSharedTemplateDO> getAdminPage(AdminSharedTemplatePageReqVO reqVO) {
        return sharedTemplateMapper.selectAdminPage(reqVO, reqVO.getAuditStatus(), reqVO.getKeyword());
    }

    @Override
    public MaxLabelSharedTemplateDO getAdmin(Long id) {
        MaxLabelSharedTemplateDO shared = sharedTemplateMapper.selectById(id);
        if (shared == null) {
            throw exception(MEMBER_SHARED_TEMPLATE_NOT_FOUND);
        }
        return shared;
    }

    @Override
    public void approve(Long id, Long operatorId) {
        MaxLabelSharedTemplateDO shared = getAdmin(id);
        if (!Objects.equals(shared.getAuditStatus(), MaxLabelSharedTemplateDO.AUDIT_PENDING)) {
            throw exception(MEMBER_SHARED_TEMPLATE_INVALID_STATUS);
        }
        LocalDateTime now = LocalDateTime.now();
        shared.setAuditStatus(MaxLabelSharedTemplateDO.AUDIT_APPROVED);
        shared.setAuditUserId(operatorId);
        shared.setAuditTime(now);
        shared.setPublishedTime(now);
        shared.setRejectReason(null);
        sharedTemplateMapper.updateById(shared);
    }

    @Override
    public void reject(Long id, Long operatorId, String reason) {
        MaxLabelSharedTemplateDO shared = getAdmin(id);
        if (!Objects.equals(shared.getAuditStatus(), MaxLabelSharedTemplateDO.AUDIT_PENDING)) {
            throw exception(MEMBER_SHARED_TEMPLATE_INVALID_STATUS);
        }
        shared.setAuditStatus(MaxLabelSharedTemplateDO.AUDIT_REJECTED);
        shared.setRejectReason(reason);
        shared.setAuditUserId(operatorId);
        shared.setAuditTime(LocalDateTime.now());
        shared.setPublishedTime(null);
        sharedTemplateMapper.updateById(shared);
    }

    @Override
    public void offline(Long id, Long operatorId) {
        MaxLabelSharedTemplateDO shared = getAdmin(id);
        if (!Objects.equals(shared.getAuditStatus(), MaxLabelSharedTemplateDO.AUDIT_APPROVED)) {
            throw exception(MEMBER_SHARED_TEMPLATE_INVALID_STATUS);
        }
        shared.setAuditStatus(MaxLabelSharedTemplateDO.AUDIT_OFFLINE);
        shared.setAuditUserId(operatorId);
        shared.setAuditTime(LocalDateTime.now());
        sharedTemplateMapper.updateById(shared);
    }

    private void requireActiveSubscription(Long memberId) {
        AppMemberSubscriptionRespVO subscription = subscriptionService.getCurrent(memberId);
        if (!Boolean.TRUE.equals(subscription.getActive())) {
            throw exception(MEMBER_SUBSCRIPTION_REQUIRED);
        }
    }

    private String sanitize(String content) {
        if (content == null || content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw exception(MEMBER_TEMPLATE_TOO_LARGE);
        }
        try {
            return MaxLabelTemplateSanitizer.sanitize(content, true);
        } catch (IllegalArgumentException error) {
            throw exception(MEMBER_TEMPLATE_INVALID);
        }
    }

}
