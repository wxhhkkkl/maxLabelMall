package cn.iocoder.yudao.module.member.service.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * .msdx 模板清洗：删除数据库口令、RFID 读写/销毁口令；共享副本额外删除数据集行。
 */
public final class MaxLabelTemplateSanitizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MaxLabelTemplateSanitizer() {
    }

    public static String sanitize(String content, boolean stripDatasetRows) {
        try {
            JsonNode root = MAPPER.readTree(content);
            JsonNode doc = root != null && root.has("doc") ? root.get("doc") : root;
            if (doc == null || !doc.isObject()) {
                return content;
            }
            JsonNode connections = doc.get("connections");
            if (connections != null && connections.isObject()) {
                connections.fields().forEachRemaining(entry -> {
                    if (entry.getValue() != null && entry.getValue().isObject()) {
                        ((ObjectNode) entry.getValue()).remove("password");
                    }
                });
            }
            JsonNode objects = doc.get("objects");
            if (objects != null && objects.isArray()) {
                for (JsonNode object : objects) {
                    if (object != null && object.isObject() && "rfid".equals(object.path("type").asText())) {
                        ((ObjectNode) object).remove("accessPwd");
                        ((ObjectNode) object).remove("killPwd");
                    }
                }
            }
            if (stripDatasetRows) {
                JsonNode datasets = doc.get("datasets");
                if (datasets != null && datasets.isObject()) {
                    datasets.fields().forEachRemaining(entry -> {
                        if (entry.getValue() != null && entry.getValue().isObject()) {
                            ((ObjectNode) entry.getValue()).remove("rows");
                        }
                    });
                }
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalArgumentException("模板内容不是有效 JSON", exception);
        }
    }

}
