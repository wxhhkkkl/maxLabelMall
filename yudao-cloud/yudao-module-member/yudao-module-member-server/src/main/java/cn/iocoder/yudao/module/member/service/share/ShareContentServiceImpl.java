package cn.iocoder.yudao.module.member.service.share;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentSaveReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppShareContentUploadReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.service.share.dto.ShareEnterpriseAccess;
import cn.iocoder.yudao.module.member.service.template.MaxLabelTemplateSanitizer;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.member.enums.LogRecordConstants.*;
import static cn.iocoder.yudao.module.member.service.share.ShareConstants.*;

@Service
@Validated
public class ShareContentServiceImpl implements ShareContentService {

    private static final Set<String> TEMPLATE_EXT = Set.of("msdx");
    private static final Set<String> TXT_EXT = Set.of("txt");
    private static final Set<String> EXCEL_EXT = Set.of("xls", "xlsx");
    private static final Set<String> CSV_EXT = Set.of("csv");
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    @Resource
    private ShareSpaceService spaceService;
    @Resource
    private ShareFolderService folderService;
    @Resource
    private ShareFileStorage fileStorage;
    @Resource
    private ShareProperties properties;
    @Resource
    private MaxLabelShareContentMapper contentMapper;

    @Override
    public PageResult<MaxLabelShareContentDO> getPage(Long memberId, AppShareContentPageReqVO reqVO) {
        if (Objects.equals(reqVO.getScope(), SCOPE_PERSONAL)) {
            return contentMapper.selectPersonalPage(memberId, reqVO.getKeyword(), reqVO.getContentType(), reqVO);
        }
        if (Objects.equals(reqVO.getScope(), SCOPE_ENTERPRISE)) {
            ShareEnterpriseAccess access = requireSeat(memberId);
            if (reqVO.getFolderId() == null) {
                throw exception(MEMBER_SHARE_FOLDER_NO_ROOT);
            }
            folderService.requireView(memberId, access.getOrgId(), reqVO.getFolderId());
            return contentMapper.selectEnterprisePage(access.getOrgId(), reqVO.getFolderId(),
                    reqVO.getKeyword(), reqVO.getContentType(), reqVO);
        }
        throw exception(MEMBER_SHARE_SCOPE_INVALID);
    }

    @Override
    public PageResult<MaxLabelShareContentDO> getMyEnterprisePage(Long memberId,
                                                                  cn.iocoder.yudao.framework.common.pojo.PageParam pageParam) {
        requireSeat(memberId);
        return contentMapper.selectMyEnterprisePage(memberId, pageParam);
    }

    @Override
    public PageResult<MaxLabelShareContentDO> getOrgPageForAdmin(Long orgId, Long folderId, String keyword,
                                                                 Integer contentType,
                                                                 cn.iocoder.yudao.framework.common.pojo.PageParam pageParam) {
        return contentMapper.selectOrgPage(orgId, folderId, keyword, contentType, pageParam);
    }

    @Override
    public MaxLabelShareContentDO getContentForAdmin(Long id) {
        MaxLabelShareContentDO content = contentMapper.selectById(id);
        if (content == null || !Objects.equals(content.getScope(), SCOPE_ENTERPRISE)) {
            throw exception(MEMBER_SHARE_CONTENT_NOT_FOUND);
        }
        return content;
    }

    @Override
    public MaxLabelShareContentDO getContent(Long memberId, Long id) {
        MaxLabelShareContentDO content = contentMapper.selectById(id);
        requireReadable(memberId, content);
        return content;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_SHARE_CONTENT_CREATE_SUB_TYPE,
            bizNo = "{{#reqVO.name}}", success = MEMBER_SHARE_CONTENT_CREATE_SUCCESS)
    public Long create(Long memberId, AppShareContentSaveReqVO reqVO) {
        Target target = prepareTarget(memberId, reqVO.getScope(), reqVO.getFolderId());
        validateContentType(reqVO.getContentType(), false);
        validateName(reqVO.getName(), reqVO.getContentType());
        if (StrUtil.isBlank(reqVO.getContent())) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
        return saveInline(target, reqVO, reqVO.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_SHARE_CONTENT_CREATE_SUB_TYPE,
            bizNo = "{{#reqVO.name}}", success = MEMBER_SHARE_CONTENT_CREATE_SUCCESS)
    public Long upload(Long memberId, AppShareContentUploadReqVO reqVO) throws Exception {
        Target target = prepareTarget(memberId, reqVO.getScope(), reqVO.getFolderId());
        Integer contentType = ShareContentTypes.infer(reqVO.getName(), reqVO.getContentType());
        validateContentType(contentType, true);
        validateName(reqVO.getName(), contentType);
        MultipartFile file = reqVO.getFile();
        if (file == null || file.isEmpty()) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
        byte[] bytes = file.getBytes();
        if (bytes.length > properties.getContentMaxBytes()) {
            throw exception(MEMBER_SHARE_CONTENT_TOO_LARGE);
        }
        if (isTextType(contentType)) {
            AppShareContentSaveReqVO saveReq = new AppShareContentSaveReqVO();
            saveReq.setName(reqVO.getName());
            saveReq.setContentType(contentType);
            saveReq.setContent(new String(bytes, StandardCharsets.UTF_8));
            saveReq.setWidthMm(reqVO.getWidthMm());
            saveReq.setHeightMm(reqVO.getHeightMm());
            saveReq.setCategory(reqVO.getCategory());
            saveReq.setKeywords(reqVO.getKeywords());
            return saveInline(target, saveReq, saveReq.getContent());
        }
        // excel / 图片：先校验配额，再落对象存储，最后写内容行
        checkQuota(target.scope(), target, bytes.length, 0);
        String fileUrl = fileStorage.store(bytes, reqVO.getName(), file.getContentType());
        MaxLabelShareContentDO content = buildContent(target, contentType, reqVO.getName(),
                STORAGE_FILE, null, fileUrl, file.getContentType(), bytes.length,
                reqVO.getWidthMm(), reqVO.getHeightMm(), reqVO.getCategory(), reqVO.getKeywords());
        contentMapper.insert(content);
        refreshUsed(target);
        return content.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long memberId, AppShareContentSaveReqVO reqVO) {
        MaxLabelShareContentDO content = contentMapper.selectById(reqVO.getId());
        requireReadable(memberId, content);
        if (!canEdit(memberId, content)) {
            throw exception(MEMBER_SHARE_CONTENT_NOT_FOUND);
        }
        validateName(reqVO.getName(), content.getContentType());
        long newSize = content.getSizeBytes() == null ? 0 : content.getSizeBytes();
        String newContent = null;
        if (reqVO.getContent() != null && isTextType(content.getContentType())) {
            newContent = sanitizeIfTemplate(content.getContentType(), reqVO.getContent());
            newSize = newContent.getBytes(StandardCharsets.UTF_8).length;
            if (newSize > properties.getContentMaxBytes()) {
                throw exception(MEMBER_SHARE_CONTENT_TOO_LARGE);
            }
        }
        checkQuota(content.getScope(), targetOf(content), newSize,
                content.getSizeBytes() == null ? 0 : content.getSizeBytes());
        MaxLabelShareContentDO update = new MaxLabelShareContentDO().setId(content.getId())
                .setName(StrUtil.trim(reqVO.getName()))
                .setWidthMm(reqVO.getWidthMm())
                .setHeightMm(reqVO.getHeightMm())
                .setCategory(reqVO.getCategory())
                .setKeywords(reqVO.getKeywords());
        if (newContent != null) {
            update.setContent(newContent).setSizeBytes(newSize)
                    .setContentHash(DigestUtil.sha256Hex(newContent));
        }
        contentMapper.updateById(update);
        refreshUsed(targetOf(content));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_SHARE_CONTENT_DELETE_SUB_TYPE,
            bizNo = "{{#id}}", success = MEMBER_SHARE_CONTENT_DELETE_SUCCESS)
    public void delete(Long memberId, Long id) {
        MaxLabelShareContentDO content = contentMapper.selectById(id);
        if (content == null) {
            return;
        }
        if (!canDelete(memberId, content)) {
            throw exception(MEMBER_SHARE_CONTENT_NOT_FOUND);
        }
        contentMapper.deleteById(id);
        refreshUsed(targetOf(content));
    }

    @Override
    public boolean canDelete(Long memberId, MaxLabelShareContentDO content) {
        if (content == null) {
            return false;
        }
        if (Objects.equals(content.getScope(), SCOPE_PERSONAL)) {
            return Objects.equals(content.getOwnerMemberId(), memberId);
        }
        ShareEnterpriseAccess access = spaceService.getEnterpriseAccess(memberId);
        if (access == null || !Boolean.TRUE.equals(access.getSeatActive())
                || !Objects.equals(access.getOrgId(), content.getOrgId())) {
            return false;
        }
        return Objects.equals(content.getOwnerMemberId(), memberId)
                || Boolean.TRUE.equals(access.getIsAdmin());
    }

    @Override
    public String getDownloadUrl(MaxLabelShareContentDO content) {
        return content != null && Objects.equals(content.getStorageType(), STORAGE_FILE)
                ? fileStorage.presign(content.getFileUrl()) : null;
    }

    // ======================= 内部 =======================

    private Long saveInline(Target target, AppShareContentSaveReqVO reqVO, String rawContent) {
        String content = sanitizeIfTemplate(reqVO.getContentType(), rawContent);
        long size = content.getBytes(StandardCharsets.UTF_8).length;
        if (size > properties.getContentMaxBytes()) {
            throw exception(MEMBER_SHARE_CONTENT_TOO_LARGE);
        }
        checkQuota(target.scope(), target, size, 0);
        java.math.BigDecimal width = reqVO.getWidthMm();
        java.math.BigDecimal height = reqVO.getHeightMm();
        if (Objects.equals(reqVO.getContentType(), TYPE_TEMPLATE)) {
            java.math.BigDecimal[] dimensions = ShareTemplateSize.resolve(content, width, height);
            width = dimensions[0];
            height = dimensions[1];
        }
        MaxLabelShareContentDO row = buildContent(target, reqVO.getContentType(), reqVO.getName(),
                STORAGE_TEXT, content, null, "", size,
                width, height, reqVO.getCategory(), reqVO.getKeywords());
        row.setContentHash(DigestUtil.sha256Hex(content));
        contentMapper.insert(row);
        refreshUsed(target);
        return row.getId();
    }

    private MaxLabelShareContentDO buildContent(Target target, Integer contentType, String name,
                                                int storageType, String content, String fileUrl, String mimeType,
                                                long size, java.math.BigDecimal widthMm, java.math.BigDecimal heightMm,
                                                String category, String keywords) {
        return MaxLabelShareContentDO.builder()
                .scope(target.scope())
                .ownerMemberId(target.ownerId())
                .orgId(target.orgId())
                .folderId(target.folderId())
                .contentType(contentType)
                .name(StrUtil.trim(name))
                .storageType(storageType)
                .content(content)
                .fileUrl(fileUrl)
                .mimeType(mimeType == null ? "" : mimeType)
                .sizeBytes(size)
                .widthMm(widthMm == null ? java.math.BigDecimal.ZERO : widthMm)
                .heightMm(heightMm == null ? java.math.BigDecimal.ZERO : heightMm)
                .category(category == null ? "" : category)
                .keywords(keywords == null ? "" : keywords)
                .build();
    }

    private void checkQuota(Integer scope, Target target, long newSize, long replacedSize) {
        Long lockOwner = Objects.equals(scope, SCOPE_PERSONAL) ? target.ownerId() : target.orgId();
        spaceService.lockSpace(scope, lockOwner);
        long used = Objects.equals(scope, SCOPE_PERSONAL)
                ? spaceService.getPersonalUsedBytes(target.ownerId())
                : spaceService.getEnterpriseUsedBytes(target.orgId());
        long quota = Objects.equals(scope, SCOPE_PERSONAL)
                ? spaceService.getPersonalQuotaBytes(target.ownerId())
                : ENTERPRISE_BYTES;
        if (used - replacedSize + newSize > quota) {
            throw exception(MEMBER_SHARE_QUOTA_EXCEEDED, used, quota);
        }
    }

    private void refreshUsed(Target target) {
        Long lockOwner = Objects.equals(target.scope(), SCOPE_PERSONAL) ? target.ownerId() : target.orgId();
        spaceService.refreshUsedBytes(target.scope(), lockOwner);
    }

    private Target targetOf(MaxLabelShareContentDO content) {
        Long owner = Objects.equals(content.getScope(), SCOPE_PERSONAL)
                ? content.getOwnerMemberId() : content.getOrgId();
        return new Target(content.getScope(), content.getOwnerMemberId(), content.getOrgId(),
                content.getFolderId());
    }

    private Target prepareTarget(Long memberId, Integer scope, Long folderId) {
        if (Objects.equals(scope, SCOPE_PERSONAL)) {
            if (folderId != null) {
                throw exception(MEMBER_SHARE_CONTENT_INVALID);
            }
            return new Target(SCOPE_PERSONAL, memberId, null, null);
        }
        if (!Objects.equals(scope, SCOPE_ENTERPRISE)) {
            throw exception(MEMBER_SHARE_SCOPE_INVALID);
        }
        ShareEnterpriseAccess access = requireWrite(memberId);
        if (folderId == null) {
            throw exception(MEMBER_SHARE_FOLDER_NO_ROOT);
        }
        folderService.requireView(memberId, access.getOrgId(), folderId);
        return new Target(SCOPE_ENTERPRISE, memberId, access.getOrgId(), folderId);
    }

    private ShareEnterpriseAccess requireSeat(Long memberId) {
        ShareEnterpriseAccess access = spaceService.getEnterpriseAccess(memberId);
        if (access == null || !Boolean.TRUE.equals(access.getSeatActive())) {
            throw exception(MEMBER_SHARE_SEAT_REQUIRED);
        }
        return access;
    }

    private ShareEnterpriseAccess requireWrite(Long memberId) {
        ShareEnterpriseAccess access = requireSeat(memberId);
        if (!Boolean.TRUE.equals(access.getLicenseActive())) {
            throw exception(MEMBER_SHARE_ENTERPRISE_READ_ONLY);
        }
        return access;
    }

    private void requireReadable(Long memberId, MaxLabelShareContentDO content) {
        if (content == null) {
            throw exception(MEMBER_SHARE_CONTENT_NOT_FOUND);
        }
        if (Objects.equals(content.getScope(), SCOPE_PERSONAL)) {
            if (!Objects.equals(content.getOwnerMemberId(), memberId)) {
                throw exception(MEMBER_SHARE_CONTENT_NOT_FOUND);
            }
            return;
        }
        if (!Objects.equals(content.getScope(), SCOPE_ENTERPRISE)) {
            throw exception(MEMBER_SHARE_CONTENT_NOT_FOUND);
        }
        ShareEnterpriseAccess access = requireSeat(memberId);
        if (!Objects.equals(access.getOrgId(), content.getOrgId())
                || (!Boolean.TRUE.equals(access.getIsAdmin()) && content.getFolderId() == null)) {
            throw exception(MEMBER_SHARE_CONTENT_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(access.getIsAdmin())) {
            folderService.requireView(memberId, content.getOrgId(), content.getFolderId());
        }
    }

    private boolean canEdit(Long memberId, MaxLabelShareContentDO content) {
        if (Objects.equals(content.getScope(), SCOPE_PERSONAL)) {
            return Objects.equals(content.getOwnerMemberId(), memberId);
        }
        ShareEnterpriseAccess access = requireWrite(memberId);
        if (!Objects.equals(access.getOrgId(), content.getOrgId())) {
            return false;
        }
        return Objects.equals(content.getOwnerMemberId(), memberId)
                || Boolean.TRUE.equals(access.getIsAdmin());
    }

    private void validateContentType(Integer contentType, boolean allowBinary) {
        if (contentType == null) {
            throw exception(MEMBER_SHARE_CONTENT_TYPE_INVALID);
        }
        boolean text = isTextType(contentType);
        boolean binary = Objects.equals(contentType, TYPE_EXCEL) || Objects.equals(contentType, TYPE_IMAGE);
        if (!text && !(allowBinary && binary)) {
            throw exception(MEMBER_SHARE_CONTENT_TYPE_INVALID);
        }
    }

    private boolean isTextType(Integer contentType) {
        return Objects.equals(contentType, TYPE_TEMPLATE)
                || Objects.equals(contentType, TYPE_TXT)
                || Objects.equals(contentType, TYPE_CSV);
    }

    private void validateName(String name, Integer contentType) {
        String value = StrUtil.trim(name);
        if (StrUtil.isBlank(value) || value.length() > 255) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
        String extension = StrUtil.subAfter(value, ".", true).toLowerCase(Locale.ROOT);
        Set<String> allowed = switch (contentType == null ? -1 : contentType) {
            case TYPE_TEMPLATE -> TEMPLATE_EXT;
            case TYPE_TXT -> TXT_EXT;
            case TYPE_EXCEL -> EXCEL_EXT;
            case TYPE_CSV -> CSV_EXT;
            case TYPE_IMAGE -> IMAGE_EXT;
            default -> Set.of();
        };
        if (!allowed.contains(extension)) {
            throw exception(MEMBER_SHARE_CONTENT_TYPE_INVALID);
        }
    }

    private String sanitizeIfTemplate(Integer contentType, String content) {
        if (StrUtil.isBlank(content)) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
        if (!Objects.equals(contentType, TYPE_TEMPLATE)) {
            return content;
        }
        try {
            return MaxLabelTemplateSanitizer.sanitize(content, false);
        } catch (IllegalArgumentException exception) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
    }

    private record Target(Integer scope, Long ownerId, Long orgId, Long folderId) {
    }

}
