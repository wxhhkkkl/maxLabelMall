package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.ExpressClient;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.ExpressClientFactory;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressMapQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link TradeOrderQueryServiceImpl} 物流轨迹部分的单元测试（010 / US35~US37）。
 *
 * 定住四件事：
 * · 未发货（没有 logisticsId）→ 空列表，**不去调三方**
 * · `kd100_code` 为空 → 抛「暂不支持」专用错误，**不去调三方**（安能这类留空的快递公司）
 * · 成功 → 按时间**倒序**（前端不再各排各的）
 * · 三方失败 → 原样抛出，由前端按错误码降级
 * · 越权 → `ORDER_NOT_FOUND`
 */
@Import(TradeOrderQueryServiceImpl.class)
public class TradeOrderQueryServiceImplTest extends BaseDbUnitTest {

    @Resource
    private TradeOrderQueryServiceImpl tradeOrderQueryService;

    @MockitoBean
    private ExpressClientFactory expressClientFactory;
    @MockitoBean
    private TradeOrderMapper tradeOrderMapper;
    @MockitoBean
    private TradeOrderItemMapper tradeOrderItemMapper;
    @MockitoBean
    private DeliveryExpressService deliveryExpressService;
    @MockitoBean
    private MemberUserApi memberUserApi;
    @MockitoBean
    private TradeExpressProperties tradeExpressProperties;

    @BeforeEach
    public void setUpMapConfig() {
        TradeExpressProperties.Kd100Config kd100 = new TradeExpressProperties.Kd100Config();
        kd100.setDefaultFromAreaId(110108); // 北京市海淀区
        when(tradeExpressProperties.getKd100()).thenReturn(kd100);
    }

    // ==================== 造数据 ====================

    private TradeOrderDO order(Long id, Long logisticsId) {
        return TradeOrderDO.builder().id(id)
                .no("NO-" + id)
                .logisticsId(logisticsId)
                .logisticsNo("SF1234567890")
                .receiverMobile("13800008888")
                .build();
    }

    private DeliveryExpressDO express(String kd100Code) {
        DeliveryExpressDO express = new DeliveryExpressDO();
        express.setId(10L);
        express.setCode("SF");
        express.setKd100Code(kd100Code);
        express.setName("顺丰速运");
        return express;
    }

    private ExpressTrackRespDTO track(String content, LocalDateTime time) {
        ExpressTrackRespDTO track = new ExpressTrackRespDTO();
        track.setContent(content);
        track.setTime(time);
        return track;
    }

    // ==================== 用例 ====================

    @Test
    public void testGetExpressTrackList_notShipped_returnsEmpty() {
        when(tradeOrderMapper.selectById(1L)).thenReturn(order(1L, null));

        assertTrue(tradeOrderQueryService.getExpressTrackList(1L).isEmpty());
        // 没有快递公司可查，连公司都不该去读
        verify(deliveryExpressService, never()).getDeliveryExpress(any());
        verify(expressClientFactory, never()).getDefaultExpressClient();
    }

    @Test
    public void testGetExpressTrackList_kd100CodeEmpty_notSupported() {
        when(tradeOrderMapper.selectById(1L)).thenReturn(order(1L, 10L));
        when(deliveryExpressService.getDeliveryExpress(10L)).thenReturn(express(""));

        assertServiceException(() -> tradeOrderQueryService.getExpressTrackList(1L),
                EXPRESS_QUERY_NOT_SUPPORTED);
        // 编码为空时**不发三方请求**（FR-261）
        verify(expressClientFactory, never()).getDefaultExpressClient();
    }

    @Test
    public void testGetExpressTrackList_success_sortedDesc() {
        when(tradeOrderMapper.selectById(1L)).thenReturn(order(1L, 10L));
        when(deliveryExpressService.getDeliveryExpress(10L)).thenReturn(express("shunfeng"));
        ExpressClient client = mock(ExpressClient.class);
        when(expressClientFactory.getDefaultExpressClient()).thenReturn(client);
        // 故意给一个乱序：服务端必须排成倒序
        when(client.getExpressTrackList(any())).thenReturn(List.of(
                track("旧", LocalDateTime.of(2026, 10, 20, 8, 0)),
                track("新", LocalDateTime.of(2026, 10, 22, 18, 30)),
                track("中", LocalDateTime.of(2026, 10, 21, 12, 0))));

        List<ExpressTrackRespDTO> tracks = tradeOrderQueryService.getExpressTrackList(1L);

        assertEquals(List.of("新", "中", "旧"), tracks.stream().map(ExpressTrackRespDTO::getContent).toList());
        // 传给三方的必须是 kd100_code，不是 code（SF）
        ArgumentCaptor<ExpressTrackQueryReqDTO> captor = ArgumentCaptor.forClass(ExpressTrackQueryReqDTO.class);
        verify(client).getExpressTrackList(captor.capture());
        assertEquals("shunfeng", captor.getValue().getExpressCode());
        assertEquals("SF1234567890", captor.getValue().getLogisticsNo());
        assertEquals("13800008888", captor.getValue().getPhone());
    }

    @Test
    public void testGetExpressTrackList_clientFailure_propagates() {
        when(tradeOrderMapper.selectById(1L)).thenReturn(order(1L, 10L));
        when(deliveryExpressService.getDeliveryExpress(10L)).thenReturn(express("shunfeng"));
        ExpressClient client = mock(ExpressClient.class);
        when(expressClientFactory.getDefaultExpressClient()).thenReturn(client);
        when(client.getExpressTrackList(any())).thenThrow(
                cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(EXPRESS_API_QUERY_FAILED));

        assertServiceException(() -> tradeOrderQueryService.getExpressTrackList(1L),
                EXPRESS_API_QUERY_FAILED);
    }

    @Test
    public void testGetExpressTrackList_notOwner_throwsOrderNotFound() {
        when(tradeOrderMapper.selectByIdAndUserId(1L, 2L)).thenReturn(null);

        assertServiceException(() -> tradeOrderQueryService.getExpressTrackList(1L, 2L),
                ORDER_NOT_FOUND);
        verify(expressClientFactory, never()).getDefaultExpressClient();
    }

    // ==================== 物流地图 ====================

    @Test
    public void testGetExpressMapUrl_success() {
        TradeOrderDO order = order(1L, 10L);
        order.setReceiverAreaId(140400); // 山西省长治市
        when(tradeOrderMapper.selectById(1L)).thenReturn(order);
        when(deliveryExpressService.getDeliveryExpress(10L)).thenReturn(express("shentong"));
        ExpressClient client = mock(ExpressClient.class);
        when(expressClientFactory.getDefaultExpressClient()).thenReturn(client);
        when(client.getExpressMapUrl(any())).thenReturn("https://map.example/trail");

        assertEquals("https://map.example/trail", tradeOrderQueryService.getExpressMapUrl(1L));

        ArgumentCaptor<ExpressMapQueryReqDTO> captor = ArgumentCaptor.forClass(ExpressMapQueryReqDTO.class);
        verify(client).getExpressMapUrl(captor.capture());
        assertEquals("shentong", captor.getValue().getExpressCode());
        assertEquals("SF1234567890", captor.getValue().getLogisticsNo());
        // 发货地址未填 → 用配置默认 110108；收件地址由 receiver_area_id 解析
        assertEquals("北京市海淀区", captor.getValue().getFrom());
        assertEquals("山西省长治市", captor.getValue().getTo());
    }

    @Test
    public void testGetExpressMapUrl_noArea_notAvailable() {
        when(tradeOrderMapper.selectById(1L)).thenReturn(order(1L, 10L));
        when(deliveryExpressService.getDeliveryExpress(10L)).thenReturn(express("shentong"));

        assertServiceException(() -> tradeOrderQueryService.getExpressMapUrl(1L),
                EXPRESS_MAP_NOT_AVAILABLE);
        verify(expressClientFactory, never()).getDefaultExpressClient();
    }

    @Test
    public void testGetExpressMapUrl_kd100CodeEmpty_notSupported() {
        TradeOrderDO order = order(1L, 10L);
        order.setReceiverAreaId(140400);
        when(tradeOrderMapper.selectById(1L)).thenReturn(order);
        when(deliveryExpressService.getDeliveryExpress(10L)).thenReturn(express(""));

        assertServiceException(() -> tradeOrderQueryService.getExpressMapUrl(1L),
                EXPRESS_QUERY_NOT_SUPPORTED);
        verify(expressClientFactory, never()).getDefaultExpressClient();
    }

    @Test
    public void testGetExpressMapUrl_notOwner_throwsOrderNotFound() {
        when(tradeOrderMapper.selectByIdAndUserId(1L, 2L)).thenReturn(null);

        assertServiceException(() -> tradeOrderQueryService.getExpressMapUrl(1L, 2L),
                ORDER_NOT_FOUND);
        verify(expressClientFactory, never()).getDefaultExpressClient();
    }

}
