package cn.iocoder.yudao.module.member.service.labelshop;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LabelshopClientTest {

    @Mock
    private LabelshopHttp http;

    @Test
    void login_parsesTokenUidAndCompanies() {
        LabelshopClient client = client();
        String body = "{\"code\":0,\"datas\":{\"token\":\"tok\",\"id\":26,"
                + "\"companys\":[{\"id\":103,\"name\":\"云马科技\"}]}}";
        when(http.postJson(anyString(), anyString(), anyMap()))
                .thenReturn(result(body));

        LabelshopSession session = client.login("user@example.com", "pwd");

        assertEquals("tok", session.getToken());
        assertEquals(26L, session.getUid());
        assertEquals(1, session.getCompanies().size());
        assertEquals("云马科技", session.getCompanies().get(0).getName());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(http).postJson(anyString(), captor.capture(), anyMap());
        assertTrue(captor.getValue().contains("\"uname\":\"user@example.com\""));
        assertTrue(captor.getValue().contains("\"pwd\":\""
                + DigestUtil.md5Hex("pwd").toUpperCase() + "\""));
    }

    @Test
    void login_throwsWhenTokenOrUidMissing() {
        LabelshopClient client = client();
        when(http.postJson(anyString(), anyString(), anyMap()))
                .thenReturn(result("{\"code\":1,\"message\":\"bad\"}"));

        assertThrows(LabelshopLoginException.class, () -> client.login("user", "pwd"));
    }

    @Test
    void getCateTree_keepsCateZeroAndLabelShop5AndOwnType() {
        LabelshopClient client = client();
        when(http.postJson(anyString(), anyString(), anyMap())).thenReturn(result(
                "{\"code\":0,\"datas\":["
                        + "{\"Cate_ID\":0,\"Parent_ID\":0,\"Name\":\"CATE0\"},"
                        + "{\"Cate_ID\":-1,\"Parent_ID\":0,\"Name\":\"LabelShop5\",\"Own_Type\":11}]}"));

        var tree = client.getCateTree(session(), 0, 1);

        assertEquals(2, tree.size());
        assertEquals(0L, tree.get(0).getCateId());
        assertNull(tree.get(0).getOwnType());
        assertEquals(-1L, tree.get(1).getCateId());
        assertEquals(11, tree.get(1).getOwnType());
    }

    @Test
    void getTemplate_returnsLabel() {
        LabelshopClient client = client();
        LabelshopSession session = session();
        when(http.postJson(anyString(), anyString(), anyMap()))
                .thenReturn(result("{\"code\":0,\"datas\":{\"data\":[{\"LABEL\":\"<xml/>\"}]}}"));

        assertEquals("<xml/>", client.getTemplate(session, "ref", 12));
    }

    @Test
    void getTemplate_throwsWhenLabelBlank() {
        LabelshopClient client = client();
        LabelshopSession session = session();
        when(http.postJson(anyString(), anyString(), anyMap()))
                .thenReturn(result("{\"code\":0,\"datas\":{\"data\":[{\"LABEL\":\"\"}]}}"));

        assertThrows(LabelshopTemplateException.class, () -> client.getTemplate(session, "ref", 12));
    }

    private LabelshopClient client() {
        LabelshopImportProperties properties = new LabelshopImportProperties();
        properties.setBaseUrl("https://example.com/v1");
        properties.setMid("mid");
        return new LabelshopClient(properties, http);
    }

    private static LabelshopSession session() {
        LabelshopSession session = new LabelshopSession();
        session.setUid(26L);
        session.setMid("mid");
        session.setToken("tok");
        return session;
    }

    private static LabelshopHttp.Result result(String body) {
        return new LabelshopHttp.Result(200, body, body.getBytes(StandardCharsets.UTF_8), Map.of());
    }

}
