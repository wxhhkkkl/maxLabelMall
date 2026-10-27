package cn.iocoder.yudao.module.member.service.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 从 .msdx 正文生成轻量 SVG 示意图。
 *
 * <p>为什么不落库缩略图：模板正文已经是唯一真相，列表接口按需生成即可；示意图只是
 * 给桌面端与审核端一个可辨认的轮廓，不追求像素级还原。超过解析上限的正文只画外框，
 * 避免列表接口被超大模板拖慢。</p>
 */
public final class MaxLabelTemplateSketch {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DATA_URL_PREFIX = "data:image/svg+xml;base64,";
    private static final int MAX_CONTENT_CHARS = 512 * 1024;
    private static final int MAX_OBJECTS = 300;
    private static final int MAX_SVG_CHARS = 120 * 1024;

    private MaxLabelTemplateSketch() {
    }

    /** 生成可直接用于 {@code <img src>} 的 SVG data URL。 */
    public static String dataUrl(String content, BigDecimal widthMm, BigDecimal heightMm) {
        double width = positive(widthMm, 60);
        double height = positive(heightMm, 40);
        String svg = outline(width, height);
        if (content != null && content.length() <= MAX_CONTENT_CHARS) {
            try {
                JsonNode root = MAPPER.readTree(content);
                JsonNode doc = root != null && root.has("doc") ? root.get("doc") : root;
                if (doc != null && doc.isObject()) {
                    double docWidth = positive(doc.path("widthMm").asDouble(0), width);
                    double docHeight = positive(doc.path("heightMm").asDouble(0), height);
                    String rendered = render(doc, docWidth, docHeight);
                    if (rendered.length() <= MAX_SVG_CHARS) {
                        svg = rendered;
                    }
                }
            } catch (Exception ignored) {
                // 正文损坏时退回空白标签示意，不影响列表加载。
            }
        }
        return DATA_URL_PREFIX + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static String outline(double width, double height) {
        StringBuilder sb = new StringBuilder(256);
        open(sb, width, height);
        frame(sb, width, height);
        return sb.append("</svg>").toString();
    }

    private static String render(JsonNode doc, double width, double height) {
        StringBuilder sb = new StringBuilder(2048);
        open(sb, width, height);
        JsonNode objects = doc.get("objects");
        if (objects != null && objects.isArray()) {
            int count = 0;
            for (JsonNode object : objects) {
                if (count++ >= MAX_OBJECTS) {
                    break;
                }
                appendObject(sb, object, 0);
            }
        }
        frame(sb, width, height);
        return sb.append("</svg>").toString();
    }

    private static void open(StringBuilder sb, double width, double height) {
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(n(width)).append(' ').append(n(height))
                .append("\" preserveAspectRatio=\"xMidYMid meet\">")
                .append("<rect width=\"").append(n(width)).append("\" height=\"").append(n(height))
                .append("\" fill=\"#ffffff\"/>");
    }

    private static void frame(StringBuilder sb, double width, double height) {
        sb.append("<rect x=\"0.4\" y=\"0.4\" width=\"").append(n(Math.max(0, width - 0.8)))
                .append("\" height=\"").append(n(Math.max(0, height - 0.8)))
                .append("\" fill=\"none\" stroke=\"#C9CDD2\" stroke-width=\"0.4\"/>");
    }

    private static void appendObject(StringBuilder sb, JsonNode object, int depth) {
        if (object == null || !object.isObject() || depth > 4) {
            return;
        }
        String type = object.path("type").asText("");
        double x = coordinate(object.path("x").asDouble(0));
        double y = coordinate(object.path("y").asDouble(0));
        double width = Math.max(0, coordinate(object.path("w").asDouble(0)));
        double height = Math.max(0, coordinate(object.path("h").asDouble(0)));
        if ("group".equals(type)) {
            JsonNode children = object.get("children");
            if (children != null && children.isArray()) {
                for (JsonNode child : children) {
                    appendObject(sb, child, depth + 1);
                }
            }
            return;
        }
        if ("text".equals(type)) {
            appendText(sb, object, x, y, width, height);
            return;
        }
        if ("barcode".equals(type)) {
            appendBarcode(sb, object, x, y, width, height);
            return;
        }
        if ("line".equals(type)) {
            appendLine(sb, object, x, y, width, height);
            return;
        }
        if ("image".equals(type)) {
            appendImage(sb, x, y, width, height);
            return;
        }
        if ("table".equals(type)) {
            appendTable(sb, object, x, y, width, height);
            return;
        }
        if ("rect".equals(type) || "ellipse".equals(type)) {
            appendShape(sb, object, type, x, y, width, height);
            return;
        }
        appendPlaceholder(sb, x, y, width, height);
    }

    private static void appendText(StringBuilder sb, JsonNode object, double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        String value = constantText(object);
        if (value.isEmpty()) {
            appendPlaceholder(sb, x, y, width, height);
            return;
        }
        double fontSize = Math.max(0.8, Math.min(height * 0.6, 6.5));
        int codePoints = value.codePointCount(0, value.length());
        double estimated = codePoints * fontSize;
        boolean bold = object.path("bold").asBoolean(false);
        sb.append("<text x=\"").append(n(x + Math.max(0.2, width * 0.01)))
                .append("\" y=\"").append(n(y + height * 0.72))
                .append("\" font-family=\"Microsoft YaHei, PingFang SC, sans-serif\" font-size=\"")
                .append(n(fontSize)).append("\" fill=\"#1A1B1C\"")
                .append(bold ? " font-weight=\"600\"" : "");
        if (estimated > width) {
            sb.append(" textLength=\"").append(n(Math.max(0, width - 0.4)))
                    .append("\" lengthAdjust=\"spacingAndGlyphs\"");
        }
        sb.append('>').append(escape(value)).append("</text>");
    }

    private static void appendBarcode(StringBuilder sb, JsonNode object, double x, double y,
                                      double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        String symbology = object.path("symbology").asText("code128");
        boolean twoDimensional = "qrcode".equals(symbology) || "microqrcode".equals(symbology)
                || "datamatrix".equals(symbology) || "pdf417".equals(symbology);
        if (twoDimensional) {
            appendTwoDimensionalCode(sb, x, y, width, height);
            return;
        }
        double barHeight = height * 0.72;
        int bars = Math.max(8, Math.min(32, (int) Math.round(width / 1.2)));
        double barWidth = width / (bars * 2.0 - 1);
        for (int index = 0; index < bars; index++) {
            if (index % 4 == 3) {
                continue;
            }
            sb.append("<rect x=\"").append(n(x + index * barWidth * 2))
                    .append("\" y=\"").append(n(y)).append("\" width=\"").append(n(Math.max(0.15, barWidth)))
                    .append("\" height=\"").append(n(barHeight))
                    .append("\" fill=\"#374151\"/>");
        }
        sb.append("<rect x=\"").append(n(x)).append("\" y=\"").append(n(y + height * 0.8))
                .append("\" width=\"").append(n(width)).append("\" height=\"")
                .append(n(Math.max(0.3, height * 0.1))).append("\" fill=\"#9CA3AF\"/>");
    }

    private static void appendTwoDimensionalCode(StringBuilder sb, double x, double y,
                                                 double width, double height) {
        double size = Math.min(width, height);
        if (size <= 0) {
            return;
        }
        double left = x + (width - size) / 2;
        double top = y + (height - size) / 2;
        int cells = 7;
        double cell = size / cells;
        for (int row = 0; row < cells; row++) {
            for (int column = 0; column < cells; column++) {
                if ((row + column) % 3 == 0) {
                    continue;
                }
                sb.append("<rect x=\"").append(n(left + column * cell))
                        .append("\" y=\"").append(n(top + row * cell))
                        .append("\" width=\"").append(n(cell * 0.86))
                        .append("\" height=\"").append(n(cell * 0.86))
                        .append("\" fill=\"#374151\"/>");
            }
        }
    }

    private static void appendLine(StringBuilder sb, JsonNode object, double x, double y,
                                   double width, double height) {
        double stroke = Math.max(0.15, Math.min(2, object.path("strokeWidth").asDouble(0.2)));
        if (width >= height) {
            sb.append("<line x1=\"").append(n(x)).append("\" y1=\"").append(n(y + height / 2))
                    .append("\" x2=\"").append(n(x + width)).append("\" y2=\"").append(n(y + height / 2))
                    .append("\" stroke=\"#9CA3AF\" stroke-width=\"").append(n(stroke)).append("\"/>");
        } else {
            sb.append("<line x1=\"").append(n(x + width / 2)).append("\" y1=\"").append(n(y))
                    .append("\" x2=\"").append(n(x + width / 2)).append("\" y2=\"").append(n(y + height))
                    .append("\" stroke=\"#9CA3AF\" stroke-width=\"").append(n(stroke)).append("\"/>");
        }
    }

    private static void appendImage(StringBuilder sb, double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        sb.append("<rect x=\"").append(n(x)).append("\" y=\"").append(n(y))
                .append("\" width=\"").append(n(width)).append("\" height=\"").append(n(height))
                .append("\" fill=\"#EDF0F4\" stroke=\"#B7BEC8\" stroke-width=\"0.25\"/>")
                .append("<line x1=\"").append(n(x)).append("\" y1=\"").append(n(y))
                .append("\" x2=\"").append(n(x + width)).append("\" y2=\"").append(n(y + height))
                .append("\" stroke=\"#B7BEC8\" stroke-width=\"0.2\"/>")
                .append("<line x1=\"").append(n(x + width)).append("\" y1=\"").append(n(y))
                .append("\" x2=\"").append(n(x)).append("\" y2=\"").append(n(y + height))
                .append("\" stroke=\"#B7BEC8\" stroke-width=\"0.2\"/>");
    }

    private static void appendTable(StringBuilder sb, JsonNode object, double x, double y,
                                    double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        sb.append("<rect x=\"").append(n(x)).append("\" y=\"").append(n(y))
                .append("\" width=\"").append(n(width)).append("\" height=\"").append(n(height))
                .append("\" fill=\"none\" stroke=\"#B7BEC8\" stroke-width=\"0.25\"/>");
        int rows = Math.max(1, Math.min(12, object.path("rows").asInt(1)));
        int columns = Math.max(1, Math.min(12, object.path("cols").asInt(1)));
        for (int index = 1; index < rows; index++) {
            double lineY = y + height * index / rows;
            sb.append("<line x1=\"").append(n(x)).append("\" y1=\"").append(n(lineY))
                    .append("\" x2=\"").append(n(x + width)).append("\" y2=\"").append(n(lineY))
                    .append("\" stroke=\"#D1D5DB\" stroke-width=\"0.18\"/>");
        }
        for (int index = 1; index < columns; index++) {
            double lineX = x + width * index / columns;
            sb.append("<line x1=\"").append(n(lineX)).append("\" y1=\"").append(n(y))
                    .append("\" x2=\"").append(n(lineX)).append("\" y2=\"").append(n(y + height))
                    .append("\" stroke=\"#D1D5DB\" stroke-width=\"0.18\"/>");
        }
    }

    private static void appendShape(StringBuilder sb, JsonNode object, String type, double x, double y,
                                    double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        boolean ellipse = "ellipse".equals(type) || "ellipse".equals(object.path("shape").asText(""));
        if (ellipse) {
            sb.append("<ellipse cx=\"").append(n(x + width / 2)).append("\" cy=\"").append(n(y + height / 2))
                    .append("\" rx=\"").append(n(width / 2)).append("\" ry=\"").append(n(height / 2))
                    .append("\" fill=\"none\" stroke=\"#B7BEC8\" stroke-width=\"0.25\"/>");
            return;
        }
        sb.append("<rect x=\"").append(n(x)).append("\" y=\"").append(n(y))
                .append("\" width=\"").append(n(width)).append("\" height=\"").append(n(height))
                .append("\" fill=\"none\" stroke=\"#B7BEC8\" stroke-width=\"0.25\"/>");
    }

    private static void appendPlaceholder(StringBuilder sb, double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        sb.append("<rect x=\"").append(n(x)).append("\" y=\"").append(n(y))
                .append("\" width=\"").append(n(width)).append("\" height=\"").append(n(height))
                .append("\" fill=\"#F3F4F6\" stroke=\"#D1D5DB\" stroke-width=\"0.18\"/>")
                .append("<line x1=\"").append(n(x + width * 0.12)).append("\" y1=\"").append(n(y + height * 0.5))
                .append("\" x2=\"").append(n(x + width * 0.88)).append("\" y2=\"").append(n(y + height * 0.5))
                .append("\" stroke=\"#D1D5DB\" stroke-width=\"0.22\"/>");
    }

    private static String constantText(JsonNode object) {
        JsonNode source = object.get("source");
        if (source == null || !"constant".equals(source.path("kind").asText(""))) {
            return "";
        }
        String value = source.path("value").asText("").trim();
        if (value.isEmpty()) {
            return "";
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= 32) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, 32));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static double positive(BigDecimal value, double fallback) {
        if (value == null) {
            return fallback;
        }
        double result = value.doubleValue();
        return Double.isFinite(result) && result > 0 ? result : fallback;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0 ? value : fallback;
    }

    private static double coordinate(double value) {
        return Double.isFinite(value) ? value : 0;
    }

    private static String n(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

}
