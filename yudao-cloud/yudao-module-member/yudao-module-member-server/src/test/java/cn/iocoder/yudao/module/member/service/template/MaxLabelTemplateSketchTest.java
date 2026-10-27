package cn.iocoder.yudao.module.member.service.template;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MaxLabelTemplateSketchTest {

    private static final String PREFIX = "data:image/svg+xml;base64,";

    private static String decode(String thumb) {
        assertTrue(thumb.startsWith(PREFIX));
        return new String(Base64.getDecoder().decode(thumb.substring(PREFIX.length())), StandardCharsets.UTF_8);
    }

    @Test
    void rendersConstantTextAndBarcodeBars() {
        String content = "{\"format\":\"maxlabel-msdx\",\"version\":2,\"doc\":{\"widthMm\":60,\"heightMm\":40,\"objects\":["
                + "{\"type\":\"text\",\"x\":4,\"y\":4,\"w\":52,\"h\":6,"
                + "\"source\":{\"kind\":\"constant\",\"value\":\"商品价格标签\"}},"
                + "{\"type\":\"barcode\",\"x\":4,\"y\":27,\"w\":52,\"h\":10,\"symbology\":\"code128\","
                + "\"source\":{\"kind\":\"constant\",\"value\":\"6901234567892\"}}]}}";

        String svg = decode(MaxLabelTemplateSketch.dataUrl(content, new BigDecimal("60"), new BigDecimal("40")));

        assertTrue(svg.contains("viewBox=\"0 0 60 40\""));
        assertTrue(svg.contains("商品价格标签"));
        assertTrue(svg.contains("fill=\"#374151\""), "条码应画成条形示意");
    }

    @Test
    void fallsBackToOutlineWhenContentIsBroken() {
        String svg = decode(MaxLabelTemplateSketch.dataUrl("not json", new BigDecimal("80"), new BigDecimal("50")));

        assertTrue(svg.contains("viewBox=\"0 0 80 50\""));
        assertFalse(svg.contains("<text"));
    }

    @Test
    void prefersDocumentDimensionsAndRecursesIntoGroups() {
        String content = "{\"doc\":{\"widthMm\":70,\"heightMm\":50,\"objects\":["
                + "{\"type\":\"group\",\"children\":[{\"type\":\"text\",\"x\":1,\"y\":1,\"w\":30,\"h\":5,"
                + "\"source\":{\"kind\":\"constant\",\"value\":\"分组文字\"}}]}]}}";

        String svg = decode(MaxLabelTemplateSketch.dataUrl(content, new BigDecimal("10"), new BigDecimal("10")));

        assertTrue(svg.contains("viewBox=\"0 0 70 50\""));
        assertTrue(svg.contains("分组文字"));
    }

    @Test
    void skipsOversizedContentInsteadOfParsingIt() {
        // 超过解析上限时只画标签外框，避免列表接口被超大模板拖垮。
        String svg = decode(MaxLabelTemplateSketch.dataUrl("x".repeat(600 * 1024),
                new BigDecimal("100"), new BigDecimal("60")));

        assertTrue(svg.contains("viewBox=\"0 0 100 60\""));
        assertFalse(svg.contains("<text"));
    }

}
