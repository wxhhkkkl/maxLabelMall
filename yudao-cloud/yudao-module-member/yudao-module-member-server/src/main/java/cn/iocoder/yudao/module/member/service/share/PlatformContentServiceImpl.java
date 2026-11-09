package cn.iocoder.yudao.module.member.service.share;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentSaveReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentStatusReqVO;
import cn.iocoder.yudao.module.member.controller.admin.share.vo.AdminPlatformContentUploadReqVO;
import cn.iocoder.yudao.module.member.controller.app.share.vo.AppPlatformContentPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelPlatformContentMapper;
import cn.iocoder.yudao.module.member.service.template.MaxLabelTemplateSanitizer;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.member.enums.LogRecordConstants.*;
import static cn.iocoder.yudao.module.member.service.share.ShareConstants.*;

@Service
@Validated
public class PlatformContentServiceImpl implements PlatformContentService {

    private static final Set<String> TEMPLATE_EXT = Set.of("msdx");
    private static final Set<String> TXT_EXT = Set.of("txt");
    private static final Set<String> EXCEL_EXT = Set.of("xls", "xlsx");
    private static final Set<String> CSV_EXT = Set.of("csv");
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    @Resource
    private MaxLabelPlatformContentMapper platformContentMapper;
    @Resource
    private ShareFileStorage fileStorage;
    @Resource
    private ShareProperties properties;

    @Override
    public PageResult<MaxLabelPlatformContentDO> getOnlinePage(AppPlatformContentPageReqVO reqVO) {
        return platformContentMapper.selectOnlinePage(reqVO.getKeyword(), reqVO.getContentType(),
                reqVO.getCategory(), reqVO.getWidthMin(), reqVO.getWidthMax(),
                reqVO.getHeightMin(), reqVO.getHeightMax(), reqVO);
    }

    @Override
    public MaxLabelPlatformContentDO getOnline(Long id) {
        MaxLabelPlatformContentDO content = platformContentMapper.selectById(id);
        if (content == null || !Objects.equals(content.getStatus(), PLATFORM_ONLINE)) {
            throw exception(MEMBER_PLATFORM_CONTENT_NOT_FOUND);
        }
        return content;
    }

    @Override
    public PageResult<MaxLabelPlatformContentDO> getAdminPage(AdminPlatformContentPageReqVO reqVO) {
        return platformContentMapper.selectAdminPage(reqVO.getKeyword(), reqVO.getContentType(),
                reqVO.getCategory(), reqVO.getStatus(), reqVO);
    }

    @Override
    public MaxLabelPlatformContentDO getAdmin(Long id) {
        MaxLabelPlatformContentDO content = platformContentMapper.selectById(id);
        if (content == null) {
            throw exception(MEMBER_PLATFORM_CONTENT_NOT_FOUND);
        }
        return content;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_PLATFORM_CONTENT_CREATE_SUB_TYPE,
            bizNo = "{{#reqVO.name}}", success = MEMBER_PLATFORM_CONTENT_CREATE_SUCCESS)
    public Long upload(Long userId, AdminPlatformContentUploadReqVO reqVO) throws Exception {
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
        MaxLabelPlatformContentDO content;
        if (isTextType(contentType)) {
            String text = sanitize(contentType, new String(bytes, StandardCharsets.UTF_8));
            java.math.BigDecimal[] dimensions = Objects.equals(contentType, TYPE_TEMPLATE)
                    ? ShareTemplateSize.resolve(text, reqVO.getWidthMm(), reqVO.getHeightMm())
                    : new java.math.BigDecimal[]{reqVO.getWidthMm(), reqVO.getHeightMm()};
            content = build(contentType, reqVO.getName(), STORAGE_TEXT, text, null, "",
                    text.getBytes(StandardCharsets.UTF_8).length,
                    dimensions[0], dimensions[1], reqVO.getCategory(), reqVO.getKeywords());
        } else {
            String fileUrl = fileStorage.store(bytes, reqVO.getName(), file.getContentType());
            content = build(contentType, reqVO.getName(), STORAGE_FILE, null, fileUrl,
                    file.getContentType(), bytes.length, reqVO.getWidthMm(), reqVO.getHeightMm(),
                    reqVO.getCategory(), reqVO.getKeywords());
        }
        content.setPublisherUserId(userId);
        content.setPublishedTime(LocalDateTime.now());
        platformContentMapper.insert(content);
        return content.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_PLATFORM_CONTENT_CREATE_SUB_TYPE,
            bizNo = "{{#reqVO.name}}", success = MEMBER_PLATFORM_CONTENT_CREATE_SUCCESS)
    public Long create(Long userId, AdminPlatformContentSaveReqVO reqVO) {
        validateContentType(reqVO.getContentType(), false);
        validateName(reqVO.getName(), reqVO.getContentType());
        if (StrUtil.isBlank(reqVO.getContent())) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
        String text = sanitize(reqVO.getContentType(), reqVO.getContent());
        java.math.BigDecimal[] dimensions = Objects.equals(reqVO.getContentType(), TYPE_TEMPLATE)
                ? ShareTemplateSize.resolve(text, reqVO.getWidthMm(), reqVO.getHeightMm())
                : new java.math.BigDecimal[]{reqVO.getWidthMm(), reqVO.getHeightMm()};
        MaxLabelPlatformContentDO content = build(reqVO.getContentType(), reqVO.getName(), STORAGE_TEXT,
                text, null, "", text.getBytes(StandardCharsets.UTF_8).length,
                dimensions[0], dimensions[1], reqVO.getCategory(), reqVO.getKeywords());
        content.setPublisherUserId(userId);
        content.setPublishedTime(LocalDateTime.now());
        platformContentMapper.insert(content);
        return content.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_PLATFORM_CONTENT_UPDATE_SUB_TYPE,
            bizNo = "{{#reqVO.id}}", success = MEMBER_PLATFORM_CONTENT_UPDATE_SUCCESS)
    public void update(Long userId, AdminPlatformContentSaveReqVO reqVO) {
        MaxLabelPlatformContentDO existing = getAdmin(reqVO.getId());
        validateName(reqVO.getName(), existing.getContentType());
        MaxLabelPlatformContentDO update = new MaxLabelPlatformContentDO().setId(existing.getId())
                .setName(StrUtil.trim(reqVO.getName()))
                .setWidthMm(reqVO.getWidthMm())
                .setHeightMm(reqVO.getHeightMm())
                .setCategory(reqVO.getCategory())
                .setKeywords(reqVO.getKeywords());
        if (reqVO.getContent() != null && isTextType(existing.getContentType())) {
            String text = sanitize(existing.getContentType(), reqVO.getContent());
            update.setContent(text)
                    .setSizeBytes((long) text.getBytes(StandardCharsets.UTF_8).length)
                    .setContentHash(DigestUtil.sha256Hex(text));
        }
        platformContentMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_PLATFORM_CONTENT_STATUS_SUB_TYPE,
            bizNo = "{{#reqVO.id}}", success = MEMBER_PLATFORM_CONTENT_STATUS_SUCCESS)
    public void updateStatus(AdminPlatformContentStatusReqVO reqVO) {
        getAdmin(reqVO.getId());
        if (!Objects.equals(reqVO.getStatus(), PLATFORM_ONLINE) && !Objects.equals(reqVO.getStatus(), PLATFORM_OFFLINE)) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
        MaxLabelPlatformContentDO update = new MaxLabelPlatformContentDO().setId(reqVO.getId())
                .setStatus(reqVO.getStatus());
        if (Objects.equals(reqVO.getStatus(), PLATFORM_ONLINE)) {
            update.setPublishedTime(LocalDateTime.now());
        }
        platformContentMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = MEMBER_SHARE_TYPE, subType = MEMBER_PLATFORM_CONTENT_DELETE_SUB_TYPE,
            bizNo = "{{#id}}", success = MEMBER_PLATFORM_CONTENT_DELETE_SUCCESS)
    public void delete(Long id) {
        platformContentMapper.deleteById(id);
    }

    @Override
    public String getDownloadUrl(MaxLabelPlatformContentDO content) {
        return content != null && Objects.equals(content.getStorageType(), STORAGE_FILE)
                ? fileStorage.presign(content.getFileUrl()) : null;
    }

    private MaxLabelPlatformContentDO build(Integer contentType, String name, int storageType,
                                            String content, String fileUrl, String mimeType, long size,
                                            java.math.BigDecimal widthMm, java.math.BigDecimal heightMm,
                                            String category, String keywords) {
        return MaxLabelPlatformContentDO.builder()
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
                .status(PLATFORM_ONLINE)
                .build();
    }

    private void validateContentType(Integer contentType, boolean allowBinary) {
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

    private String sanitize(Integer contentType, String content) {
        if (StrUtil.isBlank(content)) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
        if (!Objects.equals(contentType, TYPE_TEMPLATE)) {
            return content;
        }
        try {
            return MaxLabelTemplateSanitizer.sanitize(content, true);
        } catch (IllegalArgumentException exception) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
    }

}
