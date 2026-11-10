package cn.iocoder.yudao.module.trade.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderExcelVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderExportReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.service.invoice.TradeOrderInvoiceService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台订单导出（{@link TradeOrderController#exportOrderExcel}）。
 *
 * 钉三件事：
 * · 一笔订单一行，商品名称合并、数量汇总
 * · 时间范围是**唯一**筛选条件（2026-11-10 所有者拍板：导出忽略列表当前筛选）
 * · 会员 / 代下单人 / 开票状态都能落到表格里，而不是留空
 */
public class TradeOrderExportExcelTest extends BaseMockitoUnitTest {

    @InjectMocks
    private TradeOrderController controller;

    @Mock
    private TradeOrderQueryService tradeOrderQueryService;
    @Mock
    private MemberUserApi memberUserApi;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private TradeOrderInvoiceService tradeOrderInvoiceService;

    private static MemberUserRespDTO member(Long id, String nickname) {
        MemberUserRespDTO user = new MemberUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }

    private static AdminUserRespDTO admin(Long id, String nickname) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }

    private static TradeOrderDO order(Long id, Long userId) {
        TradeOrderDO order = TradeOrderDO.builder()
                .id(id).no("NO-" + id).userId(userId)
                .status(TradeOrderStatusEnum.UNDELIVERED.getStatus()).productCount(3)
                .payChannelCode("wx_lite").payStatus(true)
                .totalPrice(10000).discountPrice(500).deliveryPrice(800).payPrice(10300)
                .receiverName("张三").receiverMobile("13800138000")
                .receiverAreaId(110101).receiverDetailAddress("xx 路 1 号")
                .deliveryType(1).logisticsNo("SF123456")
                .remark("加急")
                .build();
        order.setCreateTime(LocalDateTime.of(2026, 11, 1, 10, 0, 0));
        order.setPayTime(LocalDateTime.of(2026, 11, 1, 10, 5, 0));
        order.setDeliveryTime(LocalDateTime.of(2026, 11, 2, 9, 0, 0));
        return order;
    }

    private static TradeOrderItemDO item(Long orderId, String spuName, Integer count) {
        TradeOrderItemDO item = new TradeOrderItemDO();
        item.setOrderId(orderId);
        item.setSpuName(spuName);
        item.setCount(count);
        return item;
    }

    @Test
    public void testBuildOrderExcelList() {
        TradeOrderDO selfOrder = order(1L, 88L);
        TradeOrderDO agentOrder = order(2L, 99L);
        agentOrder.setAgentUserId(9L);
        when(memberUserApi.getUserMap(any())).thenReturn(Map.of(
                88L, member(88L, "小明"),
                99L, member(99L, "小红")));
        when(adminUserApi.getUserMap(any())).thenReturn(Map.of(9L, admin(9L, "运营小王")));
        when(tradeOrderQueryService.getOrderItemListByOrderId(anyCollection())).thenReturn(List.of(
                item(1L, "标签纸", 1), item(1L, "色带", 2), item(2L, "标签纸", 1)));
        when(tradeOrderInvoiceService.getInvoiceStatusMapByOrderIds(any()))
                .thenReturn(Map.of(1L, 0, 2L, 10));

        List<TradeOrderExcelVO> result = controller.buildOrderExcelList(List.of(selfOrder, agentOrder));

        assertEquals(2, result.size());
        TradeOrderExcelVO self = result.get(0);
        assertEquals("NO-1", self.getNo());
        assertEquals("小明", self.getUserNickname());
        assertEquals("客户自助", self.getOrderSource());
        assertNull(self.getAgentUserName());
        assertEquals("标签纸、色带", self.getProductNames());
        assertEquals(3, self.getProductCount());
        // 金额沿用「分」，由 Excel 的 MoneyConvert 在落表时转成元
        assertEquals(10300, self.getPayPrice());
        assertEquals(500, self.getDiscountPrice());
        assertEquals(800, self.getDeliveryPrice());
        // 没退款时不能是 null（MoneyConvert 遇到 null 会 NPE）
        assertEquals(0, self.getRefundPrice());
        assertTrue(self.getReceiverAddress().contains("xx 路 1 号"));
        assertEquals("待开票", self.getInvoiceStatus());

        TradeOrderExcelVO agent = result.get(1);
        assertEquals("后台代客", agent.getOrderSource());
        assertEquals("运营小王", agent.getAgentUserName());
        assertEquals("已开票", agent.getInvoiceStatus());
    }

    @Test
    public void testBuildOrderExcelList_agentUserMissing() {
        TradeOrderDO agentOrder = order(2L, 99L);
        agentOrder.setAgentUserId(9L);
        when(memberUserApi.getUserMap(any())).thenReturn(Collections.emptyMap());
        when(adminUserApi.getUserMap(any())).thenReturn(Collections.emptyMap());
        when(tradeOrderQueryService.getOrderItemListByOrderId(anyCollection())).thenReturn(Collections.emptyList());
        when(tradeOrderInvoiceService.getInvoiceStatusMapByOrderIds(any())).thenReturn(Collections.emptyMap());

        List<TradeOrderExcelVO> result = controller.buildOrderExcelList(List.of(agentOrder));

        // 账号被删了也要能看出是后台代客，而不是留空
        assertEquals("后台用户#9", result.get(0).getAgentUserName());
        assertEquals("未申请", result.get(0).getInvoiceStatus());
        assertEquals("", result.get(0).getProductNames());
    }

    @Test
    public void testBuildOrderExcelList_empty() {
        assertTrue(controller.buildOrderExcelList(Collections.emptyList()).isEmpty());
    }

    @Test
    public void testExportOrderExcel_onlyUsesCreateTime() throws IOException {
        LocalDateTime[] createTime = {LocalDateTime.of(2026, 5, 10, 0, 0),
                LocalDateTime.of(2026, 11, 10, 23, 59, 59)};
        TradeOrderExportReqVO reqVO = new TradeOrderExportReqVO();
        reqVO.setCreateTime(createTime);
        when(tradeOrderQueryService.getOrderPage(any())).thenReturn(PageResult.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportOrderExcel(reqVO, response);

        ArgumentCaptor<TradeOrderPageReqVO> captor = ArgumentCaptor.forClass(TradeOrderPageReqVO.class);
        verify(tradeOrderQueryService).getOrderPage(captor.capture());
        TradeOrderPageReqVO used = captor.getValue();
        // 只带时间范围；列表上的其它筛选条件一律不参与（所有者口径：仅按时间范围）
        assertArrayEquals(createTime, used.getCreateTime());
        assertNull(used.getStatus());
        assertNull(used.getStatuses());
        assertNull(used.getPayChannelCode());
        assertNull(used.getDeliveryType());
        assertEquals(PageParam.PAGE_SIZE_NONE, used.getPageSize());
        // 响应里确实写了 Excel（空数据也会写表头）
        assertTrue(response.getContentAsByteArray().length > 0);
    }

}
