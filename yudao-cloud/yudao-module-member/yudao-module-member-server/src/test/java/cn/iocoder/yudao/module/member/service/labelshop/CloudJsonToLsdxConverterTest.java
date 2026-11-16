package cn.iocoder.yudao.module.member.service.labelshop;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CloudJsonToLsdxConverterTest {

    private final CloudJsonToLsdxConverter converter = new CloudJsonToLsdxConverter();

    @Test
    void convert_matchesReferenceLsdx() throws Exception {
        String json = resource("/labelshop/cloud-sample.json");
        String expected = resource("/labelshop/cloud-sample.lsdx");

        String actual = converter.convert(json);

        assertXmlEquals(parse(expected).getDocumentElement(),
                parse(actual).getDocumentElement(), "labelshopdocument");
    }

    @Test
    void convert_rejectsNonCloudJson() {
        assertThrows(LabelshopTemplateException.class, () -> converter.convert("{\"a\":1}"));
        assertThrows(LabelshopTemplateException.class, () -> converter.convert("not-json"));
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = CloudJsonToLsdxConverterTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("缺少测试资源：" + path);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static void assertXmlEquals(Element expected, Element actual, String path) {
        assertEquals(expected.getTagName(), actual.getTagName(), path + " tag");
        NamedNodeMap expectedAttrs = expected.getAttributes();
        NamedNodeMap actualAttrs = actual.getAttributes();
        assertEquals(expectedAttrs.getLength(), actualAttrs.getLength(), path + " attr count");
        for (int i = 0; i < expectedAttrs.getLength(); i++) {
            Node attr = expectedAttrs.item(i);
            assertEquals(attr.getNodeValue(), actual.getAttribute(attr.getNodeName()),
                    path + "@" + attr.getNodeName());
        }
        assertEquals(directText(expected), directText(actual), path + " text");
        NodeList expectedChildren = expected.getChildNodes();
        NodeList actualChildren = actual.getChildNodes();
        int expectedIndex = 0;
        int actualIndex = 0;
        while (true) {
            Element expectedChild = nextElement(expectedChildren, expectedIndex++);
            Element actualChild = nextElement(actualChildren, actualIndex++);
            if (expectedChild == null || actualChild == null) {
                assertEquals(expectedChild, actualChild, path + " element children");
                break;
            }
            assertXmlEquals(expectedChild, actualChild, path + "/" + expectedChild.getTagName());
        }
    }

    private static Element nextElement(NodeList nodes, int fromIndex) {
        for (int i = fromIndex; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private static String directText(Element element) {
        StringBuilder text = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            }
        }
        return text.toString().trim();
    }

}
