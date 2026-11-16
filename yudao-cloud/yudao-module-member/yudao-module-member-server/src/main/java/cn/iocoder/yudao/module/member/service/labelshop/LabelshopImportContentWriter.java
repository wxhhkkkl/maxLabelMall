package cn.iocoder.yudao.module.member.service.labelshop;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportItemMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSourceItem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.module.member.service.share.ShareConstants.SCOPE_ENTERPRISE;
import static cn.iocoder.yudao.module.member.service.share.ShareConstants.SCOPE_PERSONAL;
import static cn.iocoder.yudao.module.member.service.share.ShareConstants.STORAGE_TEXT;
import static cn.iocoder.yudao.module.member.service.share.ShareConstants.TYPE_TEMPLATE;

/**
 * 导入内容写入器：原样写入 maxlabel_share_content，并回填明细。
 *
 * <p>按 Q138 不调用 MaxLabelTemplateSanitizer；按 Q136 不校验容量。</p>
 */
@Component
public class LabelshopImportContentWriter {

    private static final Pattern PAPER_SIZE = Pattern.compile(
            "width=\"(\\d+)\"[^>]*height=\"(\\d+)\"", Pattern.CASE_INSENSITIVE);
    private static final String FORMAT_LSDX = "lsdx";

    private final MaxLabelShareContentMapper contentMapper;
    private final LabelshopImportItemMapper itemMapper;

    public LabelshopImportContentWriter(MaxLabelShareContentMapper contentMapper,
                                        LabelshopImportItemMapper itemMapper) {
        this.contentMapper = contentMapper;
        this.itemMapper = itemMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long write(LabelshopImportItemDO item, LabelshopSourceItem source, String content,
                      Long ownerMemberId, Long orgId, Long folderId) {
        return write(item, source, content, ownerMemberId, orgId, folderId, source.getFormat());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long write(LabelshopImportItemDO item, LabelshopSourceItem source, String content,
                      Long ownerMemberId, Long orgId, Long folderId, String format) {
        boolean enterprise = source.getTargetScope() != null
                && source.getTargetScope() == SCOPE_ENTERPRISE;
        boolean editable = FORMAT_LSDX.equals(format);
        String name = normalizeName(source.getSourceName(), source.getSourceRef(),
                editable ? ".lsdx" : ".json");
        int size = content.getBytes(StandardCharsets.UTF_8).length;
        BigDecimal[] dimensions = editable ? parseDimensions(content)
                : new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        MaxLabelShareContentDO row = MaxLabelShareContentDO.builder()
                .scope(enterprise ? SCOPE_ENTERPRISE : SCOPE_PERSONAL)
                .ownerMemberId(ownerMemberId)
                .orgId(enterprise ? orgId : null)
                .folderId(folderId)
                .contentType(TYPE_TEMPLATE)
                .name(name)
                .storageType(STORAGE_TEXT)
                .content(content)
                .fileUrl(null)
                .mimeType(editable ? "text/xml" : "application/json")
                .sizeBytes((long) size)
                .contentHash(DigestUtil.sha256Hex(content))
                .widthMm(dimensions[0])
                .heightMm(dimensions[1])
                .category(category(source.getSourceCategory()))
                .keywords("")
                .build();
        contentMapper.insert(row);

        item.setContentId(row.getId());
        item.setSizeBytes((long) size);
        item.setFormat(format);
        item.setEditable(editable);
        item.setStatus(1);
        item.setErrorMessage(null);
        itemMapper.updateById(item);
        return row.getId();
    }

    static String normalizeName(String rawName, String ref, String extension) {
        String name = StrUtil.blankToDefault(rawName, ref);
        name = name.replaceAll("(?i)\\.(lsdx|lsd|msdx|json)$", "");
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (StrUtil.isBlank(name)) {
            name = ref;
        }
        int max = 255 - extension.length();
        if (name.length() > max) {
            name = name.substring(0, max);
        }
        return name + extension;
    }

    private static BigDecimal[] parseDimensions(String content) {
        Matcher matcher = PAPER_SIZE.matcher(content);
        if (!matcher.find()) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
        try {
            BigDecimal width = new BigDecimal(matcher.group(1))
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal height = new BigDecimal(matcher.group(2))
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            return new BigDecimal[]{width, height};
        } catch (Exception ex) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
    }

    private static String category(String value) {
        String category = StrUtil.blankToDefault(value, "未分类");
        return category.length() > 64 ? category.substring(0, 64) : category;
    }

}
