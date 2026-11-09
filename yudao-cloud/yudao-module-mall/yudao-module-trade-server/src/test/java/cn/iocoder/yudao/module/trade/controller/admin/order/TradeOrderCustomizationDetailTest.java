package cn.iocoder.yudao.module.trade.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminCustomizationDetailRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDetailRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.service.customization.CustomizationOrderService;
import cn.iocoder.yudao.module.trade.service.offline.TradeOrderOfflinePaymentService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderLogService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 后台订单详情里的「定制要求」区块（014 / T035 / US49）
 *
 * ⚠️ 这里断言的是**详情接口真的把定制扩展行拼进去了**：
 * 只标记 `customization = true` 而不带 `customizationDetail`，后台就是一张空白的生产单。
 */
public class TradeOrderCustomizationDetailTest extends BaseMockitoUnitTest {

    @InjectMocks
    private TradeOrderController controller;

    @Mock
    private TradeOrderQueryService tradeOrderQueryService;
    @Mock
    private TradeOrderLogService tradeOrderLogService;
    @Mock
    private TradeOrderOfflinePaymentService tradeOrderOfflinePaymentService;
    @Mock
    private CustomizationOrderService customizationOrderService;
    @Mock
    private MemberUserApi memberUserApi;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private PayOrderApi payOrderApi;

    private void mockOrder(TradeOrderDO order) {
        when(tradeOrderQueryService.getOrder(order.getId())).thenReturn(order);
        when(tradeOrderQueryService.getOrderItemListByOrderId(order.getId())).thenReturn(Collections.emptyList());
        when(memberUserApi.getUser(anyLong())).thenReturn(CommonResult.success(new MemberUserRespDTO()));
    }

    private TradeOrderDO order(boolean customization) {
        return TradeOrderDO.builder()
                .id(1001L).no("NO-1001").type(0).terminal(0).userId(88L).userIp("127.0.0.1")
                .status(TradeOrderStatusEnum.UNDELIVERED.getStatus()).productCount(1).payStatus(true)
                .discountPrice(0).deliveryPrice(0).adjustPrice(0).payPrice(45520)
                .deliveryType(1).receiverName("张三").receiverMobile("13800008888")
                .receiverAreaId(110101).receiverDetailAddress("xx 路 1 号")
                .couponId(0L).couponPrice(0).pointPrice(0)
                .customization(customization)
                .build();
    }

    @Test
    public void testGetOrderDetail_agentUserNameResolved() {
        TradeOrderDO order = order(false);
        order.setAgentUserId(9L);
        mockOrder(order);
        AdminUserRespDTO agent = new AdminUserRespDTO();
        agent.setId(9L);
        agent.setNickname("运营小王");
        when(adminUserApi.getUser(9L)).thenReturn(CommonResult.success(agent));

        TradeOrderDetailRespVO result = controller.getOrderDetail(1001L).getData();
        assertEquals(9L, result.getAgentUserId());
        assertEquals("运营小王", result.getAgentUserName());
    }

    @Test
    public void testGetOrderDetail_payChannelOrderNo() {
        TradeOrderDO order = order(false);
        order.setPayOrderId(8899L);
        mockOrder(order);
        PayOrderRespDTO payOrder = new PayOrderRespDTO();
        payOrder.setChannelOrderNo("4200001234202601011234567890");
        when(payOrderApi.getOrder(8899L)).thenReturn(CommonResult.success(payOrder));

        TradeOrderDetailRespVO result = controller.getOrderDetail(1001L).getData();
        assertEquals("4200001234202601011234567890", result.getPayChannelOrderNo());
    }

    @Test
    public void testGetOrderDetail_customizationOrder() {
        TradeOrderDO order = order(true);
        mockOrder(order);
        when(customizationOrderService.getByOrderId(1001L)).thenReturn(CustomizationOrderDO.builder()
                .id(77L).orderId(1001L)
                .materialId(3L).materialName("可移除铜版")
                .width(50).height(100).quantity(1000)
                .unitArea(new BigDecimal("0.0050")).totalArea(new BigDecimal("5.0000"))
                .mode(2).form(1).dieCut(false).urgent(false)
                .priceBreakdown("{\"print\":45520,\"printing\":0,\"dieCut\":0,\"urgent\":0,"
                        + "\"subtotal\":45520,\"total\":45520}")
                .paramSnapshot("{\"material\":{\"name\":\"可移除铜版\",\"basePrice\":1138},"
                        + "\"areaRule\":{\"minArea\":0,\"maxArea\":10,\"printCoefficient\":2.0},"
                        + "\"config\":{\"printBasePrice\":20000}}")
                .files("[{\"name\":\"a.pdf\",\"url\":\"https://img.yuwangchengfeng.com/a.pdf\","
                        + "\"size\":123,\"type\":\"pdf\"}]")
                .build());

        TradeOrderDetailRespVO respVO = controller.getOrderDetail(1001L).getData();

        assertTrue(respVO.getCustomization());
        AdminCustomizationDetailRespVO detail = respVO.getCustomizationDetail();
        assertNotNull(detail);
        // 生产必要的规格字段
        assertEquals("可移除铜版", detail.getMaterialName());
        assertEquals(50, detail.getWidth());
        assertEquals(100, detail.getHeight());
        assertEquals(1000, detail.getQuantity());
        assertEquals(0, new BigDecimal("5.0000").compareTo(detail.getTotalArea()));
        // 价格明细与文件
        assertEquals(45520, detail.getBreakdown().getTotal());
        assertEquals(1, detail.getFiles().size());
        assertEquals("a.pdf", detail.getFiles().get(0).getName());
        // 快照是「下单当时」的参数，不是当前参数（SC-142）
        assertNotNull(detail.getParamSnapshot());
        Map<?, ?> snapshot = (Map<?, ?>) detail.getParamSnapshot();
        Map<?, ?> material = (Map<?, ?>) snapshot.get("material");
        assertEquals("可移除铜版", material.get("name"));
        assertEquals(1138, ((Number) material.get("basePrice")).intValue());
    }

    @Test
    public void testGetOrderDetail_normalOrder_hasNoCustomizationDetail() {
        TradeOrderDO order = order(false);
        mockOrder(order);

        TradeOrderDetailRespVO respVO = controller.getOrderDetail(1001L).getData();

        assertNull(respVO.getCustomizationDetail());
        assertFalse(Boolean.TRUE.equals(respVO.getCustomization()));
    }

    @Test
    public void testGetOrderDetail_customizationFlagWithoutExtensionRow() {
        // 标记为定制但扩展行缺失：详情不能把「没有要求」伪装成空要求之外的东西
        // （前端据此显示缺失提示，而不是崩在空指针上）
        TradeOrderDO order = order(true);
        mockOrder(order);
        when(customizationOrderService.getByOrderId(anyLong())).thenReturn(null);

        TradeOrderDetailRespVO respVO = controller.getOrderDetail(1001L).getData();

        assertTrue(respVO.getCustomization());
        assertNull(respVO.getCustomizationDetail());
    }

}
