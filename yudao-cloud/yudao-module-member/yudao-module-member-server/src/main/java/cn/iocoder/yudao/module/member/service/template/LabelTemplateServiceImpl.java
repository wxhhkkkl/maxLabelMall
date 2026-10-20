package cn.iocoder.yudao.module.member.service.template;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.subscription.vo.AppMemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppLabelTemplateSaveReqVO;
import cn.iocoder.yudao.module.member.controller.app.template.vo.AppSharedTemplatePageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelLabelTemplateDO;
import cn.iocoder.yudao.module.member.dal.dataobject.template.MaxLabelSharedTemplateDO;
import cn.iocoder.yudao.module.member.dal.mysql.template.MaxLabelLabelTemplateMapper;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

@Service
@Validated
public class LabelTemplateServiceImpl implements LabelTemplateService {

    private static final int MAX_CONTENT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TEMPLATE_COUNT = 1000;
    private static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    @Resource
    private MaxLabelLabelTemplateMapper templateMapper;
    @Resource
    private SharedTemplateService sharedTemplateService;
    @Resource
    private MemberSubscriptionService subscriptionService;

    @Override
    public PageResult<MaxLabelLabelTemplateDO> getPage(Long memberId, String keyword, PageParam pageParam) {
        return templateMapper.selectPageByMemberId(memberId, keyword, pageParam);
    }

    @Override
    public MaxLabelLabelTemplateDO getOwned(Long memberId, Long id) {
        MaxLabelLabelTemplateDO template = templateMapper.selectByIdAndMemberId(id, memberId);
        if (template == null) {
            throw exception(MEMBER_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    @Override
    public MaxLabelLabelTemplateDO getContent(Long memberId, Long id) {
        return getOwned(memberId, id);
    }

    @Override
    public MaxLabelLabelTemplateDO getById(Long id) {
        MaxLabelLabelTemplateDO template = templateMapper.selectById(id);
        if (template == null) {
            throw exception(MEMBER_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    @Override
    public Long create(Long memberId, AppLabelTemplateSaveReqVO reqVO) {
        requireActiveSubscription(memberId);
        if (reqVO.getContent() == null || reqVO.getContent().isBlank()) {
            throw exception(MEMBER_TEMPLATE_INVALID);
        }
        String content = sanitize(reqVO.getContent());
        int size = content.getBytes(StandardCharsets.UTF_8).length;
        validateQuota(memberId, size, 0, true);
        MaxLabelLabelTemplateDO template = MaxLabelLabelTemplateDO.builder()
                .memberId(memberId)
                .name(reqVO.getName().trim())
                .content(content)
                .sizeBytes(size)
                .contentHash(DigestUtil.sha256Hex(content))
                .widthMm(reqVO.getWidthMm() == null ? BigDecimal.ZERO : reqVO.getWidthMm())
                .heightMm(reqVO.getHeightMm() == null ? BigDecimal.ZERO : reqVO.getHeightMm())
                .category(reqVO.getCategory() == null ? "" : reqVO.getCategory())
                .keywords(reqVO.getKeywords() == null ? "" : reqVO.getKeywords())
                .build();
        templateMapper.insert(template);
        return template.getId();
    }

    @Override
    public void update(Long memberId, AppLabelTemplateSaveReqVO reqVO) {
        requireActiveSubscription(memberId);
        MaxLabelLabelTemplateDO template = getOwned(memberId, reqVO.getId());
        if (reqVO.getContent() != null && !reqVO.getContent().isBlank()) {
            String content = sanitize(reqVO.getContent());
            int size = content.getBytes(StandardCharsets.UTF_8).length;
            validateQuota(memberId, size, template.getSizeBytes(), false);
            template.setContent(content);
            template.setSizeBytes(size);
            template.setContentHash(DigestUtil.sha256Hex(content));
        }
        template.setName(reqVO.getName().trim());
        if (reqVO.getWidthMm() != null) template.setWidthMm(reqVO.getWidthMm());
        if (reqVO.getHeightMm() != null) template.setHeightMm(reqVO.getHeightMm());
        if (reqVO.getCategory() != null) template.setCategory(reqVO.getCategory());
        if (reqVO.getKeywords() != null) template.setKeywords(reqVO.getKeywords());
        templateMapper.updateById(template);
    }

    @Override
    public void delete(Long memberId, Long id) {
        getOwned(memberId, id);
        sharedTemplateService.deleteBySource(id);
        templateMapper.deleteById(id);
    }

    @Override
    public PageResult<MaxLabelSharedTemplateDO> getSharedPage(AppSharedTemplatePageReqVO reqVO) {
        return sharedTemplateService.getPublicPage(reqVO);
    }

    @Override
    public MaxLabelSharedTemplateDO getSharedContent(Long id) {
        return sharedTemplateService.getPublicContent(id);
    }

    @Override
    public void requireActiveSubscription(Long memberId) {
        AppMemberSubscriptionRespVO subscription = subscriptionService.getCurrent(memberId);
        if (!Boolean.TRUE.equals(subscription.getActive())) {
            throw exception(MEMBER_SUBSCRIPTION_REQUIRED);
        }
    }

    private String sanitize(String content) {
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw exception(MEMBER_TEMPLATE_TOO_LARGE);
        }
        try {
            return MaxLabelTemplateSanitizer.sanitize(content, false);
        } catch (IllegalArgumentException error) {
            throw exception(MEMBER_TEMPLATE_INVALID);
        }
    }

    private void validateQuota(Long memberId, int newBytes, int replacedBytes, boolean creating) {
        var templates = templateMapper.selectListByMemberId(memberId);
        long count = templates.size() + (creating ? 1 : 0);
        long total = templates.stream().mapToLong(item -> item.getSizeBytes() == null ? 0 : item.getSizeBytes()).sum()
                + newBytes - replacedBytes;
        if (count > MAX_TEMPLATE_COUNT || total > MAX_TOTAL_BYTES) {
            throw exception(MEMBER_TEMPLATE_QUOTA_EXCEEDED);
        }
    }

}
