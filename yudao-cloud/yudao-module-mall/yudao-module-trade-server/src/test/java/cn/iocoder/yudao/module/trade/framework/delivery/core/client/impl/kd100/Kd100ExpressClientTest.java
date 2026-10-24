package cn.iocoder.yudao.module.trade.framework.delivery.core.client.impl.kd100;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressMapQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_API_QUERY_ERROR;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_API_QUERY_FAILED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_MAP_NOT_CONFIGURED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_QUERY_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link Kd100ExpressClient} 的单测（010 / FR-259 / R49）。
 *
 * 两件最容易回归的事：
 * · 失败时**不能把快递100 的原文**塞进业务异常 —— 它会顺着 CommonResult.msg 漏给客户
 * · `com` 必须是**小写**（快递100 的要求），且 `num` / `phone` 要真的传过去
 */
public class Kd100ExpressClientTest {

    private RestTemplate restTemplate;
    private Kd100ExpressClient client;

    @BeforeEach
    public void setUp() {
        restTemplate = mock(RestTemplate.class);
        TradeExpressProperties.Kd100Config config = new TradeExpressProperties.Kd100Config();
        config.setCustomer("customer-test");
        config.setKey("key-test");
        client = new Kd100ExpressClient(restTemplate, config);
    }

    private void stubResponse(String json) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));
    }

    @Test
    public void testGetExpressTrackList_failed_doesNotLeakThirdPartyMessage() {
        stubResponse("{\"result\":\"false\",\"message\":\"快递公司编码错误，请检查 com 参数\"}");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getExpressTrackList(req("SF", "123", "13800008888")));

        assertEquals(EXPRESS_API_QUERY_FAILED.getCode(), ex.getCode());
        // 关键断言：三方原文**不能**出现在给客户看的文案里
        assertFalse(ex.getMessage().contains("快递公司编码错误"), "实际：" + ex.getMessage());
        assertEquals(EXPRESS_API_QUERY_FAILED.getMsg(), ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetExpressTrackList_success_requestMapping() {
        stubResponse("{\"result\":\"true\",\"data\":[{\"time\":\"2026-10-22 18:30:00\",\"context\":\"已签收\"}]}");

        client.getExpressTrackList(req("SF", "123", "13800008888"));

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        MultiValueMap<String, String> body = (MultiValueMap<String, String>) captor.getValue().getBody();
        String param = body.getFirst("param");
        // com 必须小写；num / phone 原样带上
        assertTrue(param.contains("\"com\":\"sf\""), "实际：" + param);
        assertTrue(param.contains("\"num\":\"123\""), "实际：" + param);
        assertTrue(param.contains("\"phone\":\"13800008888\""), "实际：" + param);
    }

    @Test
    public void testGetExpressTrackList_invalidCompany_mapsToNotSupported() {
        // 快递100 对「com 编码无法识别」的返回：result=false + returnCode=400 + 明确提到编码
        stubResponse("{\"result\":\"false\",\"returnCode\":\"400\",\"message\":\"快递公司编码错误，请检查\"}");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getExpressTrackList(req("wrong-code", "123", "13800008888")));

        assertEquals(EXPRESS_QUERY_NOT_SUPPORTED.getCode(), ex.getCode());
    }

    @Test
    public void testGetExpressTrackList_transportError_mapsToQueryError() {
        // 超时/连接失败是 RestClientException，必须转成业务错误，服务层才能记订单号并让前端降级
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getExpressTrackList(req("SF", "123", "13800008888")));

        assertEquals(EXPRESS_API_QUERY_ERROR.getCode(), ex.getCode());
    }

    @Test
    public void testGetExpressTrackList_emptyBody_mapsToQueryError() {
        stubResponse("");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getExpressTrackList(req("SF", "123", "13800008888")));

        assertEquals(EXPRESS_API_QUERY_ERROR.getCode(), ex.getCode());
    }

    @Test
    public void testGetExpressTrackList_malformedJson_mapsToQueryError() {
        stubResponse("{not-json");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getExpressTrackList(req("SF", "123", "13800008888")));

        assertEquals(EXPRESS_API_QUERY_ERROR.getCode(), ex.getCode());
    }

    private ExpressTrackQueryReqDTO req(String code, String no, String phone) {
        return new ExpressTrackQueryReqDTO().setExpressCode(code).setLogisticsNo(no).setPhone(phone);
    }

    // ==================== 物流地图 ====================

    private Kd100ExpressClient clientWithMap() {
        TradeExpressProperties.Kd100Config config = new TradeExpressProperties.Kd100Config();
        config.setCustomer("customer-test");
        config.setKey("key-test");
        config.setMapConfigKey("template-id");
        return new Kd100ExpressClient(restTemplate, config);
    }

    private ExpressMapQueryReqDTO mapReq() {
        return new ExpressMapQueryReqDTO().setExpressCode("STO").setLogisticsNo("123")
                .setPhone("13800008888").setFrom("山西省太原市小店区").setTo("山西省长治市");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetExpressMapUrl_success_requestMapping() {
        stubResponse("{\"trailUrl\":\"https://map.example/trail\",\"message\":\"ok\"}");

        assertEquals("https://map.example/trail", clientWithMap().getExpressMapUrl(mapReq()));

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        MultiValueMap<String, String> body = (MultiValueMap<String, String>) captor.getValue().getBody();
        String param = body.getFirst("param");
        assertTrue(param.contains("\"com\":\"sto\""), "实际：" + param);
        assertTrue(param.contains("\"from\":\"山西省太原市小店区\""), "实际：" + param);
        assertTrue(param.contains("\"to\":\"山西省长治市\""), "实际：" + param);
        assertTrue(param.contains("\"mapConfigKey\":\"template-id\""), "实际：" + param);
    }

    @Test
    public void testGetExpressMapUrl_notConfigured() {
        // 基础 client 没有 mapConfigKey / mapFrom
        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getExpressMapUrl(mapReq()));

        assertEquals(EXPRESS_MAP_NOT_CONFIGURED.getCode(), ex.getCode());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    public void testGetExpressMapUrl_providerError() {
        stubResponse("{\"result\":\"false\",\"returnCode\":\"400\",\"message\":\"寄件地址不能为空\"}");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> clientWithMap().getExpressMapUrl(mapReq()));

        assertEquals(EXPRESS_API_QUERY_FAILED.getCode(), ex.getCode());
    }

}
