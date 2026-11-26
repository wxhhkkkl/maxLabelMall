package cn.iocoder.yudao.module.member.service.labelshop;

import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.service.share.ShareFileStorage;
import cn.iocoder.yudao.module.member.service.share.ShareProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LabelshopImageImporterTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final LabelshopImageImporter.Target PERSONAL =
            new LabelshopImageImporter.Target(1, 1001L, null, 600L, "我的模板库/上衣");
    private static final LabelshopImageImporter.Target ENTERPRISE =
            new LabelshopImageImporter.Target(2, 1001L, 88L, 500L, "企业/组织模板/云马");

    @Mock private LabelshopHttp http;
    @Mock private LabelshopImportProperties properties;
    @Mock private ShareProperties shareProperties;
    @Mock private ShareFileStorage fileStorage;
    @Mock private MaxLabelShareContentMapper contentMapper;

    private LabelshopImageImporter importer;
    private Map<String, LabelshopImageImporter.ImageData> cache;

    @BeforeEach
    void setUp() {
        // 显式构造，避免 @InjectMocks 对构造参数的猜测
        importer = new LabelshopImageImporter(http, properties, shareProperties, fileStorage, contentMapper);
        cache = new HashMap<>();
        org.mockito.Mockito.lenient().when(properties.getImageBaseUrl())
                .thenReturn("https://cvsimg.oss-cn-beijing.aliyuncs.com/li/");
        org.mockito.Mockito.lenient().when(shareProperties.getContentMaxBytes())
                .thenReturn(16L * 1024 * 1024);
    }

    @Test
    void rewrite_downloadsAndEmbedsLabelshopPicture() {
        when(http.get(eq("https://cvsimg.oss-cn-beijing.aliyuncs.com/li/AoAZ.png"), anyMap()))
                .thenReturn(new LabelshopHttp.Result(200, "", PNG, Map.of()));
        when(fileStorage.store(eq(PNG), eq("20071211.png"), eq("image/png")))
                .thenReturn("https://oss.example/maxlabel-share/20071211.png");

        LabelshopImageImporter.Result result = importer.rewrite(pictureXml(
                "labelshop:\\\\codingv\\AoAZ.png", "20071211.png"), ENTERPRISE, cache);

        String embedded = Base64.getEncoder().encodeToString(PNG);
        assertTrue(result.content().contains("type=\"0\""),
                result.content() + " | warnings=" + result.warnings());
        assertTrue(result.content().contains(embedded));
        assertFalse(result.content().contains("labelshop:"));
        assertTrue(result.warnings().isEmpty());

        ArgumentCaptor<MaxLabelShareContentDO> captor =
                ArgumentCaptor.forClass(MaxLabelShareContentDO.class);
        verify(contentMapper).insert(captor.capture());
        MaxLabelShareContentDO row = captor.getValue();
        assertEquals(2, row.getScope());
        assertEquals(1001L, row.getOwnerMemberId());
        assertEquals(88L, row.getOrgId());
        assertEquals(500L, row.getFolderId());
        assertEquals(5, row.getContentType());
        assertEquals(2, row.getStorageType());
        assertEquals("20071211.png", row.getName());
        assertEquals("https://oss.example/maxlabel-share/20071211.png", row.getFileUrl());
        assertEquals("image/png", row.getMimeType());
        assertEquals((long) PNG.length, row.getSizeBytes());
        // srcheight=2159 / srcwidth=2337 是 1/100 mm，落到图片内容要除以 100
        assertEquals(new BigDecimal("21.59"), row.getHeightMm());
        assertEquals(new BigDecimal("23.37"), row.getWidthMm());
        assertEquals("企业/组织模板/云马", row.getCategory());
    }

    @Test
    void rewrite_ignoresPathTraversalReferences() {
        String xml = pictureXml("labelshop:\\\\codingv\\..\\other.png", "other.png");

        LabelshopImageImporter.Result result = importer.rewrite(xml, PERSONAL, cache);

        assertEquals(xml, result.content());
        verify(http, never()).get(anyString(), anyMap());
    }

    @Test
    void rewrite_ignoresNonLabelshopFileReferences() {
        String xml = pictureXml("pics/a.png", "a.png");

        LabelshopImageImporter.Result result = importer.rewrite(xml, PERSONAL, cache);

        assertEquals(xml, result.content());
        assertTrue(result.warnings().isEmpty());
        verify(http, never()).get(anyString(), anyMap());
        verify(contentMapper, never()).insert(any(MaxLabelShareContentDO.class));
    }

    @Test
    void rewrite_keepsTemplateWhenImageDownloadFails() {
        when(http.get(anyString(), anyMap()))
                .thenReturn(new LabelshopHttp.Result(404, "", new byte[0], Map.of()));

        String xml = pictureXml("labelshop:\\\\codingv\\missing.png", "missing.png");
        LabelshopImageImporter.Result result = importer.rewrite(xml, PERSONAL, cache);

        assertEquals(xml, result.content());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("missing.png"));
        verify(contentMapper, never()).insert(any(MaxLabelShareContentDO.class));
    }

    @Test
    void rewrite_rejectsNonImagePayload() {
        byte[] html = "<html>error</html>".getBytes(StandardCharsets.UTF_8);
        when(http.get(anyString(), anyMap()))
                .thenReturn(new LabelshopHttp.Result(200, "", html, Map.of()));

        String xml = pictureXml("labelshop:\\\\codingv\\bad.png", "bad.png");
        LabelshopImageImporter.Result result = importer.rewrite(xml, PERSONAL, cache);

        assertEquals(xml, result.content());
        assertEquals(1, result.warnings().size());
        verify(fileStorage, never()).store(any(), anyString(), anyString());
    }

    @Test
    void rewrite_movesNativeImgAttributeToTextContent() {
        String xml = "<labelshopdocument><labelform><labelobjects>"
                + "<drawobj type=\"drawpicture\" id=\"1\"><picture type=\"0\" img=\"QUJD\" pname=\"a.png\"/>"
                + "</drawobj></labelobjects></labelform></labelshopdocument>";

        LabelshopImageImporter.Result result = importer.rewrite(xml, PERSONAL, cache);

        assertTrue(result.content().contains(">QUJD</picture>"));
        assertFalse(result.content().contains("img="));
        verify(http, never()).get(anyString(), anyMap());
        verify(contentMapper, never()).insert(any(MaxLabelShareContentDO.class));
    }

    @Test
    void rewrite_downloadsOnceWhenSameImageUsedTwice() {
        when(http.get(anyString(), anyMap()))
                .thenReturn(new LabelshopHttp.Result(200, "", PNG, Map.of()));
        when(fileStorage.store(any(), anyString(), anyString())).thenReturn("https://oss/x.png");
        String xml = "<labelshopdocument><labelform><labelobjects>"
                + "<drawobj type=\"drawpicture\" id=\"1\"><picture type=\"1\" "
                + "file=\"labelshop:\\\\codingv\\same.png\" pname=\"same.png\"/></drawobj>"
                + "<drawobj type=\"drawpicture\" id=\"2\"><picture type=\"1\" "
                + "file=\"labelshop:\\\\codingv\\same.png\" pname=\"same.png\"/></drawobj>"
                + "</labelobjects></labelform></labelshopdocument>";

        importer.rewrite(xml, PERSONAL, cache);

        verify(http, times(1)).get(anyString(), anyMap());
        verify(contentMapper, times(1)).insert(any(MaxLabelShareContentDO.class));
    }

    @Test
    void rewrite_reusesTaskCacheAcrossTemplates() {
        when(http.get(anyString(), anyMap()))
                .thenReturn(new LabelshopHttp.Result(200, "", PNG, Map.of()));
        when(fileStorage.store(any(), anyString(), anyString())).thenReturn("https://oss/x.png");
        String xml = pictureXml("labelshop:\\\\codingv\\logo.png", "logo.png");

        importer.rewrite(xml, PERSONAL, cache);
        importer.rewrite(xml, PERSONAL, cache);

        verify(http, times(1)).get(anyString(), anyMap());
        verify(contentMapper, times(1)).insert(any(MaxLabelShareContentDO.class));
    }

    @Test
    void rewrite_keepsOriginalWhenEmbeddingExceedsLimit() {
        when(shareProperties.getContentMaxBytes()).thenReturn(20L);
        when(http.get(anyString(), anyMap()))
                .thenReturn(new LabelshopHttp.Result(200, "", PNG, Map.of()));
        when(fileStorage.store(any(), anyString(), anyString())).thenReturn("https://oss/x.png");
        String xml = pictureXml("labelshop:\\\\codingv\\big.png", "big.png");

        LabelshopImageImporter.Result result = importer.rewrite(xml, PERSONAL, cache);

        assertEquals(xml, result.content());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("16 MB") || result.warnings().get(0).contains("超过"));
    }

    @Test
    void rewrite_keepsOriginalOnMalformedXml() {
        String xml = "<labelshopdocument><labelform><picture type=\"1\" file=\"labelshop:\\\\codingv\\x.png\">";

        LabelshopImageImporter.Result result = importer.rewrite(xml, PERSONAL, cache);

        assertEquals(xml, result.content());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void rewrite_keepsEmbeddedPictureWithoutImgAttribute() {
        String xml = "<labelshopdocument><labelform><labelobjects>"
                + "<drawobj type=\"drawpicture\" id=\"1\"><picture type=\"0\">QUJD</picture></drawobj>"
                + "</labelobjects></labelform></labelshopdocument>";

        LabelshopImageImporter.Result result = importer.rewrite(xml, PERSONAL, cache);

        assertEquals(xml, result.content());
        assertTrue(result.warnings().isEmpty());
        verify(http, never()).get(anyString(), anyMap());
    }

    @Test
    void downloadUrl_escapesFileName() {
        when(http.get(anyString(), anyMap()))
                .thenReturn(new LabelshopHttp.Result(200, "", PNG, Map.of()));
        when(fileStorage.store(any(), anyString(), anyString())).thenReturn("https://oss/x.png");

        importer.rewrite(pictureXml("labelshop:\\\\codingv\\a b.png", "a b.png"), PERSONAL, cache);

        verify(http).get(eq("https://cvsimg.oss-cn-beijing.aliyuncs.com/li/a%20b.png"), anyMap());
    }

    @Test
    void rewrite_usesFileNameWhenPnameMissing() {
        when(http.get(anyString(), anyMap()))
                .thenReturn(new LabelshopHttp.Result(200, "", PNG, Map.of()));
        when(fileStorage.store(any(), anyString(), anyString())).thenReturn("https://oss/x.png");
        String xml = pictureXml("labelshop:\\\\codingv\\no-name.png", null);

        importer.rewrite(xml, PERSONAL, cache);

        ArgumentCaptor<MaxLabelShareContentDO> captor =
                ArgumentCaptor.forClass(MaxLabelShareContentDO.class);
        verify(contentMapper).insert(captor.capture());
        assertEquals("no-name.png", captor.getValue().getName());
    }

    @Test
    void failedImageIsNotRetriedWithinSameTemplate() {
        when(http.get(anyString(), anyMap()))
                .thenReturn(new LabelshopHttp.Result(500, "", new byte[0], Map.of()));
        String xml = "<labelshopdocument><labelform><labelobjects>"
                + "<drawobj type=\"drawpicture\" id=\"1\"><picture type=\"1\" "
                + "file=\"labelshop:\\\\codingv\\x.png\" pname=\"x.png\"/></drawobj>"
                + "<drawobj type=\"drawpicture\" id=\"2\"><picture type=\"1\" "
                + "file=\"labelshop:\\\\codingv\\x.png\" pname=\"x.png\"/></drawobj>"
                + "</labelobjects></labelform></labelshopdocument>";

        LabelshopImageImporter.Result result = importer.rewrite(xml, PERSONAL, cache);

        verify(http, times(1)).get(anyString(), anyMap());
        assertEquals(1, result.warnings().size());
        assertTrue(cache.containsKey("600|x.png"));
        assertNull(cache.get("600|x.png").bytes());
    }

    private static String pictureXml(String file, String pname) {
        String pnameAttr = pname == null ? "" : " pname=\"" + pname + "\"";
        return "<labelshopdocument><labelform><labelobjects>"
                + "<drawobj type=\"drawpicture\" id=\"1\"><picture type=\"1\" "
                + "file=\"" + file + "\"" + pnameAttr + " srcheight=\"2159\" srcwidth=\"2337\"/>"
                + "</drawobj></labelobjects></labelform></labelshopdocument>";
    }

}
