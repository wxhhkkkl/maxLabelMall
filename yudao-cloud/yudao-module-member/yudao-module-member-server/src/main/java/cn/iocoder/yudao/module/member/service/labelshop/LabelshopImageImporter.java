package cn.iocoder.yudao.module.member.service.labelshop;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.service.share.ShareFileStorage;
import cn.iocoder.yudao.module.member.service.share.ShareProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.module.member.service.share.ShareConstants.*;

/**
 * 导入模板里的图片处理。
 *
 * <p>LabelShop 外链图片以 {@code labelshop:\\codingv\<name>} 记录，原客户端从自己的图片空间读取；
 * 导入到商城后这个地址不可达。这里按文件名从 LabelShop 图片 OSS 取图，写入目标共享空间（图片类型），
 * 并把 {@code <picture>} 从链接式改成内嵌式（正文 base64），使桌面端无需额外下载即可显示。</p>
 *
 * <p>图片失败只记警告，不阻断模板导入（所有者 2026-11-26 决策）。</p>
 */
@Slf4j
@Component
public class LabelshopImageImporter {

    private static final String LABELSHOP_PREFIX = "labelshop:";
    private static final String IMAGE_HOST = "codingv";
    /** 下载失败的缓存占位：同一任务内不重复请求同一张图片 */
    public static final ImageData FAILED = new ImageData(null, null, null);
    private static final String IMAGE_EXT = "png";
    private static final int MAX_NAME = 255;

    private final LabelshopHttp http;
    private final LabelshopImportProperties properties;
    private final ShareProperties shareProperties;
    private final ShareFileStorage fileStorage;
    private final MaxLabelShareContentMapper contentMapper;

    public LabelshopImageImporter(LabelshopHttp http, LabelshopImportProperties properties,
                                  ShareProperties shareProperties, ShareFileStorage fileStorage,
                                  MaxLabelShareContentMapper contentMapper) {
        this.http = http;
        this.properties = properties;
        this.shareProperties = shareProperties;
        this.fileStorage = fileStorage;
        this.contentMapper = contentMapper;
    }

    public Result rewrite(String content, Target target, Map<String, ImageData> cache) {
        if (StrUtil.isBlank(content) || !content.contains("<picture")) {
            return new Result(content, List.of());
        }
        Document document;
        try {
            document = parse(content);
        } catch (Exception ex) {
            return new Result(content, List.of("模板 XML 解析失败，已跳过图片处理"));
        }
        NodeList pictures = document.getElementsByTagName("picture");
        List<String> warnings = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < pictures.getLength(); i++) {
            Element picture = (Element) pictures.item(i);
            if (moveImgAttribute(picture)) {
                changed = true;
            }
            if (!Objects.equals(picture.getAttribute("type"), "1")) {
                continue;
            }
            String reference = picture.getAttribute("file");
            String path = labelshopPath(reference);
            if (path == null) {
                continue;
            }
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            String key = target.folderId() + "|" + fileName;
            ImageData data = cache.get(key);
            if (data == null) {
                try {
                    data = downloadAndStore(fileName, picture.getAttribute("pname"), path, target,
                            picture.getAttribute("srcwidth"), picture.getAttribute("srcheight"));
                } catch (RuntimeException ex) {
                    log.warn("[rewrite][图片 {} 导入失败]", fileName, ex);
                    warnings.add("图片 " + fileName + " 导入失败：" + ex.getMessage());
                    data = FAILED;
                }
                cache.put(key, data);
            }
            if (data == FAILED || data.bytes() == null) {
                if (data == FAILED && !containsWarning(warnings, fileName)) {
                    warnings.add("图片 " + fileName + " 导入失败");
                }
                continue;
            }
            picture.setAttribute("type", "0");
            picture.removeAttribute("file");
            picture.removeAttribute("img");
            picture.setTextContent(Base64.getEncoder().encodeToString(data.bytes()));
            changed = true;
        }
        if (!changed) {
            return new Result(content, warnings);
        }
        String rewritten;
        try {
            rewritten = serialize(document);
        } catch (Exception ex) {
            return new Result(content, append(warnings, "模板回写失败，已保留原模板"));
        }
        long size = rewritten.getBytes(StandardCharsets.UTF_8).length;
        if (size > shareProperties.getContentMaxBytes()) {
            return new Result(content, append(warnings, "内嵌图片后超过 16 MB，已保留链接式图片"));
        }
        return new Result(rewritten, warnings);
    }

    // ======================= 图片下载 / 入库 =======================

    private ImageData downloadAndStore(String fileName, String pname, String path, Target target,
                                       String srcWidth, String srcHeight) {
        String url = imageUrl(path);
        LabelshopHttp.Result response = http.get(url, Map.of());
        if (response.status() != 200 || response.bytes() == null || response.bytes().length == 0) {
            throw new LabelshopTemplateException("HTTP " + response.status());
        }
        byte[] bytes = response.bytes();
        if (bytes.length > shareProperties.getContentMaxBytes()) {
            throw new LabelshopTemplateException("图片超过 16 MB");
        }
        String mime = imageMime(bytes);
        if (mime == null) {
            throw new LabelshopTemplateException("返回内容不是图片");
        }
        String name = imageName(StrUtil.blankToDefault(pname, fileName));
        String fileUrl = fileStorage.store(bytes, name, mime);
        contentMapper.insert(MaxLabelShareContentDO.builder()
                .scope(target.scope())
                .ownerMemberId(target.ownerMemberId())
                .orgId(target.scope() == SCOPE_ENTERPRISE ? target.orgId() : null)
                .folderId(target.folderId())
                .contentType(TYPE_IMAGE)
                .name(name)
                .storageType(STORAGE_FILE)
                .fileUrl(fileUrl)
                .mimeType(mime)
                .sizeBytes((long) bytes.length)
                .widthMm(mm(srcWidth))
                .heightMm(mm(srcHeight))
                .contentHash(DigestUtil.sha256Hex(bytes))
                .category(category(target.category()))
                .keywords("")
                .build());
        return new ImageData(bytes, mime, name);
    }

    private String imageUrl(String path) {
        String base = StrUtil.blankToDefault(properties.getImageBaseUrl(),
                "https://cvsimg.oss-cn-beijing.aliyuncs.com/li/");
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        String[] segments = path.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!encoded.isEmpty()) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return base + encoded;
    }

    /**
     * 把 {@code labelshop:\\codingv\a/b.png} 归一成 {@code a/b.png}；不是 LabelShop 图片返回 null。
     */
    static String labelshopPath(String reference) {
        if (reference == null || !reference.startsWith(LABELSHOP_PREFIX)) {
            return null;
        }
        String rest = reference.substring(LABELSHOP_PREFIX.length());
        rest = rest.replace('\\', '/');
        while (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        int slash = rest.indexOf('/');
        if (slash <= 0 || !IMAGE_HOST.equalsIgnoreCase(rest.substring(0, slash))) {
            return null;
        }
        String path = rest.substring(slash + 1);
        if (path.isBlank()) {
            return null;
        }
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return path;
    }

    private static boolean moveImgAttribute(Element picture) {
        String img = picture.getAttribute("img");
        if (img == null || img.isBlank()) {
            return false;
        }
        String text = picture.getTextContent();
        if (text == null || text.isBlank()) {
            picture.setTextContent(img);
        }
        picture.removeAttribute("img");
        return true;
    }

    private static String imageName(String rawName) {
        String name = rawName.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (StrUtil.isBlank(name)) {
            name = "image." + IMAGE_EXT;
        } else if (!name.contains(".")) {
            name = name + "." + IMAGE_EXT;
        }
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot) : "." + IMAGE_EXT;
        int max = MAX_NAME - ext.length();
        if (name.length() > MAX_NAME) {
            name = name.substring(0, Math.max(1, max)) + ext;
        }
        return name;
    }

    /** LabelShop 的 srcwidth / srcheight 是 1/100 mm，转成内容表使用的 mm。 */
    private static BigDecimal mm(String hundredths) {
        if (StrUtil.isBlank(hundredths)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(hundredths).divide(BigDecimal.valueOf(100), 2,
                    RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String category(String value) {
        String category = StrUtil.blankToDefault(value, "未分类");
        return category.length() > 64 ? category.substring(0, 64) : category;
    }

    static String imageMime(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes[0] == 'B' && bytes[1] == 'M') {
            return "image/bmp";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    // ======================= XML =======================

    private static Document parse(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    // ======================= 辅助 =======================

    private static boolean containsWarning(List<String> warnings, String fileName) {
        return warnings.stream().anyMatch(warning -> warning.contains(fileName));
    }

    private static List<String> append(List<String> warnings, String warning) {
        List<String> result = new ArrayList<>(warnings);
        result.add(warning);
        return result;
    }

    /** 导入目标：与模板相同的空间、文件夹与分类。 */
    public record Target(int scope, Long ownerMemberId, Long orgId, Long folderId, String category) {
    }

    /** 单张图片的下载结果；bytes 为 null 表示失败。 */
    public record ImageData(byte[] bytes, String mime, String name) {
    }

    /** 重写结果：content 可能等于入参（未改动或回退）。 */
    public record Result(String content, List<String> warnings) {
    }

}
