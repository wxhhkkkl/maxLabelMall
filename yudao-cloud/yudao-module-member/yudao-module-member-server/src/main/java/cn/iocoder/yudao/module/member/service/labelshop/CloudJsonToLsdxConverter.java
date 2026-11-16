package cn.iocoder.yudao.module.member.service.labelshop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

/**
 * LabelShop 6 云端模板 JSON → LabelShop 5 `.lsdx` XML。
 *
 * <p>逐行对照 `labelshop-export/cloud_to_lsdx.py` 移植：桌面端只认 `.lsdx`，
 * 云端模板（`Own_Type=0`）必须转换后才能打开/编辑。</p>
 */
@Component
public class CloudJsonToLsdxConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 转换；不是云端模板 JSON 时抛 {@link LabelshopTemplateException}。 */
    public String convert(String cloudJson) {
        JsonNode doc;
        try {
            doc = MAPPER.readTree(cloudJson);
        } catch (Exception ex) {
            throw new LabelshopTemplateException("云端模板 JSON 解析失败");
        }
        if (doc == null || !doc.isObject() || !doc.path("lfrm").isObject()) {
            throw new LabelshopTemplateException("不是 LabelShop 云端模板 JSON");
        }
        try {
            return toXml(doc);
        } catch (LabelshopTemplateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LabelshopTemplateException("云端模板转换失败：" + ex.getMessage());
        }
    }

    private String toXml(JsonNode doc) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = document.createElement("labelshopdocument");
        document.appendChild(root);
        root.setAttribute("version", text(doc.get("ver"), "1.5"));
        copyRootAttributes(root, doc, "tag", "date", "sign", "docid", "fnc");

        JsonNode lfrm = object(doc.get("lfrm"));
        Element labelform = sub(root, "labelform");
        labelform.setAttribute("version", "1.1");

        JsonNode paper = object(lfrm.get("paper"));
        Element paperEl = sub(labelform, "paper");
        addAttrs(paperEl, paper, Set.of("page", "prn"), Map.of(), Set.of());
        Element pageEl = sub(paperEl, "paper");
        addAttrs(pageEl, object(paper.get("page")), Set.of(), Map.of(), Set.of("color"));
        Element printerEl = sub(paperEl, "printer");
        addAttrs(printerEl, object(paper.get("prn")), Set.of(), Map.of(), Set.of());

        JsonNode layers = lfrm.get("leayers");
        JsonNode layer = layers != null && layers.isArray() && !layers.isEmpty()
                ? layers.get(0) : MAPPER.createObjectNode();
        JsonNode meta = object(layer.get("label"));
        Element labellayer = sub(labelform, "labellayer");

        Element fmt = sub(labellayer, "labelformat");
        fmt.setAttribute("name", text(meta.get("name"), ""));
        fmt.setAttribute("uint", text(meta.get("uint"), "0"));

        Element pgEl = sub(fmt, "page");
        addAttrs(pgEl, object(meta.get("page")), Set.of(),
                Map.of("l", "left", "t", "top", "r", "right", "b", "bottom", "v", "viewrotate"),
                Set.of());

        Element lbEl = sub(fmt, "label");
        addAttrs(lbEl, object(meta.get("label")), Set.of(),
                Map.of("w", "width", "h", "height", "r", "rows", "c", "cols",
                        "rg", "rowgap", "cg", "colgap", "ro", "rotation"),
                Set.of());

        JsonNode form = object(meta.get("form"));
        Element fmEl = sub(fmt, "form");
        fmEl.setAttribute("corner", text(form.get("cr"), "0"));
        fmEl.setAttribute("hole", text(form.get("ho"), "0"));
        fmEl.setAttribute("holesize", text(form.get("hs"), "0"));
        fmEl.setAttribute("order", "LTH");

        Element objsEl = sub(labellayer, "labelobjects");
        JsonNode objs = layer.get("objs");
        if (objs != null && objs.isArray()) {
            for (JsonNode obj : objs) {
                drawObj(objsEl, obj);
            }
        }
        JsonNode link = layer.get("links");
        if (truthyValue(link)) {
            Element linkEl = sub(labellayer, "objvarlink");
            linkEl.setAttribute("link", text(link, ""));
        }

        Element varsEl = sub(labelform, "variables");
        JsonNode vars = lfrm.get("vars");
        if (vars != null && vars.isArray()) {
            for (JsonNode var : vars) {
                variable(varsEl, var);
            }
        }

        Element dbEl = sub(root, "database");
        addAttrs(dbEl, object(doc.get("dbs")), Set.of(), Map.of("ver", "version"), Set.of());
        sub(root, "pictures");
        Element pf = sub(root, "printform");
        pf.setAttribute("qty", "0");
        pf.setAttribute("copy", "0");
        pf.setAttribute("enter", "0");
        pf.setAttribute("mark", "0");
        pf.setAttribute("all", "0");
        pf.setAttribute("srarch", "0");
        pf.setAttribute("goto", "0");
        pf.setAttribute("title", "");

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private void copyRootAttributes(Element root, JsonNode doc, String... keys) {
        for (String key : keys) {
            JsonNode value = doc.get(key);
            if (value != null && !value.isNull() && !value.isContainerNode()) {
                root.setAttribute(key, text(value, ""));
            }
        }
    }

    private void addAttrs(Element el, JsonNode data, Set<String> skip,
                          Map<String, String> rename, Set<String> colorKeys) {
        if (data == null || !data.isObject()) {
            return;
        }
        data.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (skip.contains(key) || value == null || value.isNull() || value.isContainerNode()) {
                return;
            }
            String attribute = rename.getOrDefault(key, key);
            String text = colorKeys.contains(key)
                    ? color(value, "bkcolor".equals(key) ? "255,255,255" : "0,0,0")
                    : text(value, "");
            el.setAttribute(attribute, text);
        });
    }

    private void font(Element parent, JsonNode font) {
        Element el = sub(parent, "font");
        addAttrs(el, object(font), Set.of(), Map.of(), Set.of("color", "bkcolor"));
    }

    private void drawObj(Element parent, JsonNode obj) {
        int type = obj.path("type").asInt(-1);
        switch (type) {
            case 2 -> {
                Element el = drawBase(parent, obj, "drawtext");
                subWithAttrs(el, "color", object(obj.get("clr")), Set.of(), Map.of(), Set.of());
                JsonNode textNode = obj.get("text");
                Element textEl = sub(el, "text");
                if (textNode != null && textNode.isObject()) {
                    addAttrs(textEl, textNode, Set.of(), Map.of(), Set.of());
                } else if (textNode != null && !textNode.isNull() && !textNode.isContainerNode()) {
                    textEl.setTextContent(text(textNode, ""));
                }
                font(el, obj.get("font"));
            }
            case 4 -> {
                Element el = drawBase(parent, obj, "drawline");
                subWithAttrs(el, "color", object(obj.get("clr")), Set.of(), Map.of(), Set.of());
                subWithAttrs(el, "line", object(obj.get("line")), Set.of(), Map.of(),
                        Set.of("linecolor"));
            }
            case 1 -> {
                Element el = drawBase(parent, obj, "drawbarcode");
                subWithAttrs(el, "color", object(obj.get("clr")), Set.of(), Map.of(), Set.of());
                subWithAttrs(el, "barcode", object(obj.get("code")), Set.of(), Map.of(), Set.of());
                font(el, obj.get("font"));
                subWithAttrs(el, "baroption", object(obj.get("option")), Set.of(), Map.of(), Set.of());
                subWithAttrs(el, "baroption2", object(obj.get("option2")), Set.of(), Map.of(), Set.of());
            }
            case 3 -> {
                Element el = drawBase(parent, obj, "drawpicture");
                subWithAttrs(el, "color", object(obj.get("clr")), Set.of(), Map.of(), Set.of());
                subWithAttrs(el, "picture", object(obj.get("picture")), Set.of(), Map.of(), Set.of());
            }
            case 8 -> {
                Element el = drawBase(parent, obj, "drawrfid");
                subWithAttrs(el, "color", object(obj.get("clr")), Set.of(), Map.of(), Set.of());
                JsonNode rfidNode = object(obj.get("rfid"));
                Element rfid = sub(el, "rfid");
                rfid.setAttribute("bank", "EPC");
                rfid.setAttribute("lock", truthyValue(rfidNode.get("kl")) ? "1" : "0");
                rfid.setAttribute("startblock", text(rfidNode.get("sb"), "0"));
                rfid.setAttribute("datatype", truthyValue(rfidNode.get("dt")) ? "hex" : "ascii");
                rfidNode.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    if (value != null && !value.isNull() && !value.isContainerNode()) {
                        rfid.setAttribute(entry.getKey(), text(value, ""));
                    }
                });
            }
            default -> {
            }
        }
    }

    /** 对应 Python `_base_drawobj`：返回新元素，供子节点继续挂载。 */
    private Element drawBase(Element parent, JsonNode obj, String tag) {
        Element el = sub(parent, "drawobj");
        el.setAttribute("type", tag);
        el.setAttribute("id", text(obj.get("id"), ""));
        el.setAttribute("name", text(obj.get("name"), ""));
        for (String key : new String[]{"left", "top", "right", "bottom", "style",
                "halign", "valign", "tpalign", "tvalign"}) {
            JsonNode value = obj.get(key);
            if (value != null && !value.isNull() && !value.isContainerNode()) {
                el.setAttribute(key, text(value, ""));
            }
        }
        String rot = text(obj.get("rotation"), "0");
        el.setAttribute("rotation", ("0".equals(rot) || "none".equals(rot)) ? "none" : rot);
        return el;
    }

    private void variable(Element parent, JsonNode var) {
        Element el = sub(parent, "variable");
        int type = var.path("type").asInt(0);
        el.setAttribute("type", switch (type) {
            case 1 -> "serial";
            case 2 -> "database";
            case 3 -> "date";
            case 4 -> "time";
            case 5 -> "script";
            case 6 -> "keyboard";
            default -> "constant";
        });
        var.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (key.equals("type") || key.equals("limit") || key.equals("cut")
                    || value == null || value.isNull() || value.isContainerNode()) {
                return;
            }
            el.setAttribute(key, text(value, ""));
        });
        JsonNode limit = object(var.get("limit"));
        if (!limit.isEmpty()) {
            Element limitEl = sub(el, "limit");
            limitEl.setAttribute("filled", switch (limit.path("filled").asInt(0)) {
                case 1 -> "right";
                case 2 -> "center";
                default -> "left";
            });
            int fillchar = limit.path("fillchar").asInt(0);
            limitEl.setAttribute("fillchar", fillchar > 0 ? String.valueOf((char) fillchar) : "");
            limitEl.setAttribute("cutout", switch (limit.path("cutout").asInt(0)) {
                case 1 -> "left";
                case 2 -> "center";
                default -> "right";
            });
        }
        sub(el, "cut");
    }

    // ======================= DOM / 值工具 =======================

    private static Element sub(Element parent, String tag) {
        Element el = parent.getOwnerDocument().createElement(tag);
        parent.appendChild(el);
        return el;
    }

    private static Element subWithAttrs(Element parent, String tag, JsonNode data,
                                        Set<String> skip, Map<String, String> rename,
                                        Set<String> colorKeys) {
        Element el = sub(parent, tag);
        if (data != null && data.isObject()) {
            data.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (skip.contains(key) || value == null || value.isNull() || value.isContainerNode()) {
                    return;
                }
                String attribute = rename.getOrDefault(key, key);
                String text = colorKeys.contains(key)
                        ? color(value, "bkcolor".equals(key) ? "255,255,255" : "0,0,0")
                        : text(value, "");
                el.setAttribute(attribute, text);
            });
        }
        return el;
    }

    private static JsonNode object(JsonNode node) {
        return node != null && node.isObject() ? node : MAPPER.createObjectNode();
    }

    /** 对应 Python 的真值判断：空串 / 0 / false / 空容器为假。 */
    private static boolean truthyValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.asDouble() != 0;
        }
        if (node.isContainerNode()) {
            return node.size() > 0;
        }
        return !node.asText().isEmpty();
    }

    private static String text(JsonNode node, String fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "1" : "0";
        }
        if (node.isContainerNode()) {
            return fallback;
        }
        return node.asText();
    }

    private static String color(JsonNode node, String fallback) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return fallback;
        }
        int value = node.asInt() & 0xFFFFFF;
        return (value & 0xFF) + "," + ((value >> 8) & 0xFF) + "," + ((value >> 16) & 0xFF);
    }

}
