package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.controller.app.customization.vo.AppCustomizationOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationOrderCreateResultBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationQuoteBO;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定制建单：改价不建单（Q101）与 60 秒幂等（Q99）
 */
@Import(CustomizationOrderServiceImpl.class)
public class CustomizationOrderServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CustomizationOrderService orderService;
    @Resource
    private CustomizationOrderMapper customizationOrderMapper;
    @Resource
    private TradeOrderMapper tradeOrderMapper;

    @MockitoBean
    private CustomizationPriceService priceService;
    @MockitoBean
    private CustomizationSpecValidator specValidator;
    @MockitoBean
    private TradeOrderUpdateService tradeOrderUpdateService;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private AppCustomizationOrderCreateReqVO reqVO(int expectedAmount) {
        AppCustomizationOrderCreateReqVO reqVO = new AppCustomizationOrderCreateReqVO();
        reqVO.setMaterialId(3L);
        reqVO.setWidth(50);
        reqVO.setHeight(100);
        reqVO.setQuantity(1000);
        reqVO.setMode(2);
        reqVO.setForm(2);
        reqVO.setRowCount(1);
        reqVO.setColumnCount(1);
        reqVO.setExpectedAmount(expectedAmount);
        reqVO.setDeliveryType(1);
        return reqVO;
    }

    private CustomizationQuoteBO quote(int payable) {
        CustomizationQuoteBO quote = new CustomizationQuoteBO();
        quote.setMaterialId(3L);
        quote.setMaterialName("可移除铜版");
        quote.setUnitArea(new BigDecimal("0.0050"));
        quote.setTotalArea(new BigDecimal("5.0000"));
        quote.setPayable(payable);
        CustomizationQuoteBO.Breakdown breakdown = new CustomizationQuoteBO.Breakdown();
        breakdown.setPrint(payable);
        breakdown.setSubtotal(payable);
        breakdown.setTotal(payable);
        quote.setBreakdown(breakdown);
        quote.setParamSnapshot("{}");
        return quote;
    }

    @Test
    public void testCreate_priceChanged_doesNotCreateOrder() {
        when(priceService.quote(any(), any(), any(), any())).thenReturn(quote(100));

        CustomizationOrderCreateResultBO result = orderService.createOrder(9L, reqVO(200));

        assertTrue(result.isChanged());
        assertEquals(100, result.getQuote().getPayable());
        verify(tradeOrderUpdateService, never()).createOrder(any(), any());
        assertEquals(0, customizationOrderMapper.selectList().size());
    }

    @Test
    public void testCreate_success_persistsCustomization() {
        when(priceService.quote(any(), any(), any(), any())).thenReturn(quote(100));
        when(valueOperations.setIfAbsent(anyString(), eq("PENDING"), any(Duration.class))).thenReturn(true);
        TradeOrderDO order = TradeOrderDO.builder().id(123L).payOrderId(456L).build();
        when(tradeOrderUpdateService.createOrder(eq(9L), any())).thenReturn(order);

        CustomizationOrderCreateResultBO result = orderService.createOrder(9L, reqVO(100));

        assertFalse(result.isChanged());
        assertEquals(123L, result.getOrderId());
        assertEquals(456L, result.getPayOrderId());
        assertEquals(1, customizationOrderMapper.selectList().size());
        assertEquals(123L, customizationOrderMapper.selectByOrderId(123L).getOrderId());
    }

    @Test
    public void testCreate_duplicateWithin60s_returnsSameOrder() {
        when(priceService.quote(any(), any(), any(), any())).thenReturn(quote(100));
        when(valueOperations.setIfAbsent(anyString(), eq("PENDING"), any(Duration.class))).thenReturn(false);
        // 已有订单
        TradeOrderDO existing = TradeOrderDO.builder()
                .no("NO-EXIST").type(0).terminal(0).userId(9L).userIp("127.0.0.1")
                .status(TradeOrderStatusEnum.UNPAID.getStatus()).productCount(1).payStatus(false)
                .discountPrice(0).deliveryPrice(0).adjustPrice(0).payPrice(100)
                .deliveryType(1).couponId(0L).couponPrice(0).pointPrice(0).build();
        tradeOrderMapper.insert(existing);
        when(valueOperations.get(anyString())).thenReturn(String.valueOf(existing.getId()));

        CustomizationOrderCreateResultBO result = orderService.createOrder(9L, reqVO(100));

        assertTrue(result.isDuplicated());
        assertEquals(existing.getId(), result.getOrderId());
        verify(tradeOrderUpdateService, never()).createOrder(any(), any());
    }

}
