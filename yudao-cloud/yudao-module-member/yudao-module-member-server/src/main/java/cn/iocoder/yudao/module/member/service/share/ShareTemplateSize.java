package cn.iocoder.yudao.module.member.service.share;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

/**
 * 从 .msdx 正文解析标签宽高；调用方已显式给出正数宽高时以调用方为准。
 */
public final class ShareTemplateSize {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShareTemplateSize() {
    }

    public static BigDecimal[] resolve(String content, BigDecimal widthMm, BigDecimal heightMm) {
        if (positive(widthMm) && positive(heightMm)) {
            return new BigDecimal[]{widthMm, heightMm};
        }
        try {
            JsonNode root = MAPPER.readTree(content);
            JsonNode doc = root != null && root.has("doc") ? root.get("doc") : root;
            if (doc == null || !doc.isObject()) {
                return new BigDecimal[]{widthMm, heightMm};
            }
            BigDecimal parsedWidth = decimal(doc.get("widthMm"));
            BigDecimal parsedHeight = decimal(doc.get("heightMm"));
            return new BigDecimal[]{
                    positive(widthMm) ? widthMm : parsedWidth,
                    positive(heightMm) ? heightMm : parsedHeight
            };
        } catch (Exception ignored) {
            return new BigDecimal[]{widthMm, heightMm};
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return BigDecimal.ZERO;
        }
        return node.decimalValue();
    }

}
