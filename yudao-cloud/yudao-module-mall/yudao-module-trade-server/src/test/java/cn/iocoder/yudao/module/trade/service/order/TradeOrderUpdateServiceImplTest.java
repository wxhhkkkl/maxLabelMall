package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.product.api.subscriptionplan.dto.ProductSubscriptionPlanRespDTO;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.pay.api.refund.PayRefundApi;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.product.api.comment.ProductCommentApi;
import cn.iocoder.yudao.module.promotion.api.combination.CombinationRecordApi;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderCancelReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementRespVO;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderOfflineConfirmReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.offline.TradeOrderOfflinePaymentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.offline.TradeOrderOfflinePaymentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderCancelTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.cart.CartService;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryPickUpStoreService;
import cn.iocoder.yudao.module.trade.service.invoice.TradeOrderInvoiceService;
import cn.iocoder.yudao.module.trade.service.message.TradeMessageService;
import cn.iocoder.yudao.module.trade.service.offline.TradeOrderOfflinePaymentService;
import cn.iocoder.yudao.module.trade.service.offline.TradeOrderOfflinePaymentServiceImpl;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeProductSkuOrderHandler;
import cn.iocoder.yudao.module.trade.service.price.TradePriceService;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * {@link TradeOrderUpdateServiceImpl} 的单元测试 —— **只测 008 对公转账新增的那几件事**（US27）
 *
 * ⚠️ 本类是本模块**第一个**要 mock 一大串外部 API 的服务测试。为什么值得：
 * 「确认收款」是**资金动作**，它要同时定住四件事（订单进待发货 / 痕迹回填 / 幂等 / 各种拒绝），
 * 而这些只有把 orderService 真跑起来才验得到。
 *
 * 三条最要紧的断言：
 * · **确认收款后订单进「待发货」**，且是走 `updateOrderPaid`（唯一入口）—— 不自己改状态（R43）
 * · **重复确认只生效一次**（FR-206），第二次报「该订单已收款」
 * · **对公待收款单不进超时自动取消**（FR-212 / R45），且 `payOrderId = null`
 *   **不会被传进 pay RPC**（只改主筛选、漏了那句兜底，就是这个坑）
 */
@Import({TradeOrderUpdateServiceImpl.class, TradeOrderOfflinePaymentServiceImpl.class})
public class TradeOrderUpdateServiceImplTest extends BaseDbUnitTest {

    @Resource
    private TradeOrderUpdateServiceImpl orderService; // ⚠️ 用实现类：`cancelOrderBySystem(order)` 不在接口上
    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private TradeOrderItemMapper tradeOrderItemMapper;
    @Resource
    private TradeOrderOfflinePaymentMapper offlinePaymentMapper;
    @Resource
    private TradeOrderOfflinePaymentService offlinePaymentService;

    // ========== 别的模块的 API / 别的域的 Service：一律 mock（BaseDbUnitTest 的既定用法）==========
    @MockitoBean
    private PayOrderApi payOrderApi;
    @MockitoBean
    private PayRefundApi payRefundApi;
    @MockitoBean
    private MemberAddressApi addressApi;
    @MockitoBean
    private ProductCommentApi productCommentApi;
    @MockitoBean
    private cn.iocoder.yudao.module.product.api.subscriptionplan.ProductSubscriptionPlanApi productSubscriptionPlanApi;
    @MockitoBean
    private cn.iocoder.yudao.module.member.api.org.MemberOrgApi memberOrgApi;
    @MockitoBean
    private SocialClientApi socialClientApi;
    @MockitoBean
    private CombinationRecordApi combinationRecordApi;
    @MockitoBean
    private CartService cartService;
    @MockitoBean
    private TradeOrderInvoiceService tradeOrderInvoiceService;
    @MockitoBean
    private TradeConfigService tradeConfigService;
    @MockitoBean
    private TradePriceService tradePriceService;
    @MockitoBean
    private DeliveryExpressService deliveryExpressService;
    @MockitoBean
    private TradeMessageService tradeMessageService;
    @MockitoBean
    private DeliveryPickUpStoreService pickUpStoreService;
    @MockitoBean
    private TradeNoRedisDAO tradeNoRedisDAO;
    @MockitoBean
    private TradeOrderProperties tradeOrderProperties;
    /**
     * ⚠️ `tradeOrderHandlers` 是一个 `List<TradeOrderHandler>`（@Resource 注入）：
     * **一个都不注册会让 Spring 起不来**，所以这里显式给一个 mock（它的回调什么都不做，
     * 正好是单测想要的）。
     */
    @MockitoBean
    private TradeProductSkuOrderHandler tradeOrderHandler;

    @BeforeEach
    public void setUp() {
        // 超时自动取消的期限：线上是 2h（`yudao.trade.order.pay-expire-time`）
        when(tradeOrderProperties.getPayExpireTime()).thenReturn(Duration.ofHours(2));
        // 支付单查询：默认"查不到"（= 没有已支付的在线支付单，不影响取消）
        when(payOrderApi.getOrder(any())).thenReturn(CommonResult.success(null));
        // 收货地址：默认"没有默认地址"（结算页那条路会读它）
        when(addressApi.getDefaultAddress(any())).thenReturn(CommonResult.success(null));
        // 012：默认不是企业计划 / 不是企业会员 —— 既有用例全部走个人路径
        lenient().when(productSubscriptionPlanApi.getBySkuId(any())).thenReturn(CommonResult.success(null));
        lenient().when(memberOrgApi.getByMemberId(any())).thenReturn(CommonResult.success(null));
        // 发货订阅消息：sendDeliveryOrderMessage 会调用它，默认 null 会 NPE
        when(socialClientApi.sendWxaSubscribeMessage(any())).thenReturn(CommonResult.success(true));
    }

    // ==================== 造数据 ====================

    /**
     * 造一笔订单。
     *
     * @param payChannelCode 支付成功的渠道；**对公待收款单这里是 null**（确认收款前都空着）
     * @param payOrderId     在线支付单号；对公单没有，传 null
     */
    private TradeOrderDO createOrder(Integer status, String payChannelCode, Long payOrderId,
                                     LocalDateTime createTime) {
        TradeOrderDO order = TradeOrderDO.builder()
                .no("NO-" + System.nanoTime()).type(0).terminal(0).userId(88L).userIp("127.0.0.1")
                .status(status).productCount(1).payStatus(!TradeOrderStatusEnum.isUnpaid(status))
                .discountPrice(0).deliveryPrice(0).adjustPrice(0).payPrice(10800).deliveryType(1)
                .payChannelCode(payChannelCode).payOrderId(payOrderId)
                .receiverName("张三").receiverMobile("13800008888").receiverAreaId(110101)
                .receiverDetailAddress("xx 路 1 号")
                .refundStatus(TradeOrderRefundStatusEnum.NONE.getStatus())
                .couponId(0L).couponPrice(0).pointPrice(0)
                .build();
        // ⚠️ `createTime` 来自 BaseDO，**不在 Lombok 的 builder 里**；必须在 insert 前手工塞。
        //    （MP 的 insert 自动填充用的是 strictInsertFill，只补 null，所以这里塞的值会被保留）
        order.setCreateTime(createTime);
        tradeOrderMapper.insert(order);

        // ⚠️ **必须造一条订单项**：`TradeOrderConvert#convert(order, orderItems, ...)` 会取
        //    `orderItems.get(0).getSpuName()` 当支付单的 subject —— 没有项会直接下标越界。
        //    （真实订单不可能没有项，所以这不是生产缺陷，是本测试的造数要求。）
        //    这条项也让「确认收款」能真的走完 `updateOrderPaid`
        TradeOrderItemDO orderItem = new TradeOrderItemDO();
        orderItem.setOrderId(order.getId());
        orderItem.setUserId(order.getUserId());
        orderItem.setSpuId(10L);
        orderItem.setSpuName("三防热敏标签纸");
        orderItem.setSkuId(100L);
        orderItem.setCount(1);
        orderItem.setPrice(order.getPayPrice());
        orderItem.setDiscountPrice(0);
        orderItem.setPayPrice(order.getPayPrice());
        orderItem.setAfterSaleStatus(0);
        tradeOrderItemMapper.insert(orderItem);
        return order;
    }

    /** 一笔**待收款**的对公单：未支付、没有在线支付单、子表有行 */
    private TradeOrderDO createWaitingOfflineOrder() {
        TradeOrderDO order = createOrder(TradeOrderStatusEnum.UNPAID.getStatus(), null, null,
                LocalDateTime.now());
        offlinePaymentService.createOfflinePayment(order.getId());
        return order;
    }

    /**
     * 让「确认收款」走通：造支付单返回 9999，再让**随后 `updateOrderPaid` 的复核**查到一张
     * 形态正确的支付单（SUCCESS + 金额 / 商户单号对得上）。
     *
     * ⚠️ 这几项**不能随便写** —— `updateOrderPaid` 会逐项校验，少一项就抛
     * `..._PAY_PRICE_NOT_MATCH` / `..._PAY_ORDER_ID_ERROR`，订单永远进不了「待发货」（R43）。
     */
    private void stubPaidOfflinePayOrder(TradeOrderDO order) {
        when(payOrderApi.createPaidOfflineOrder(any())).thenReturn(CommonResult.success(9999L));
        PayOrderRespDTO payOrder = new PayOrderRespDTO();
        payOrder.setId(9999L);
        payOrder.setStatus(PayOrderStatusEnum.SUCCESS.getStatus());
        payOrder.setChannelCode(PayChannelEnum.OFFLINE.getCode());
        payOrder.setChannelOrderNo("OFFLINE-1");
        payOrder.setMerchantOrderId(String.valueOf(order.getId()));
        payOrder.setPrice(order.getPayPrice());
        payOrder.setSuccessTime(LocalDateTime.now());
        when(payOrderApi.getOrder(9999L)).thenReturn(CommonResult.success(payOrder));
    }

    private TradeOrderOfflineConfirmReqVO confirmReqVO(Long orderId, String bankSerialNo) {
        TradeOrderOfflineConfirmReqVO reqVO = new TradeOrderOfflineConfirmReqVO();
        reqVO.setId(orderId);
        reqVO.setBankSerialNo(bankSerialNo);
        reqVO.setPayerName("南京路桥机械有限公司");
        reqVO.setPaidTime(LocalDateTime.of(2026, 10, 16, 10, 0));
        reqVO.setRemark("分两笔打的，这是第一笔");
        return reqVO;
    }

    // ==================== 确认收款（T479 / SC-071 / SC-072）====================

    @Test
    public void testConfirmOfflinePaid_success() {
        TradeOrderDO order = createWaitingOfflineOrder();
        stubPaidOfflinePayOrder(order);

        orderService.confirmOfflinePaid(confirmReqVO(order.getId(), "2026101600012345"), 249L);

        // 1. 订单进「待发货」—— 与在线支付成功**完全同一个落点**（FR-208）
        TradeOrderDO dbOrder = tradeOrderMapper.selectById(order.getId());
        assertEquals(TradeOrderStatusEnum.UNDELIVERED.getStatus(), dbOrder.getStatus());
        assertTrue(dbOrder.getPayStatus());
        assertNotNull(dbOrder.getPayTime());
        // 渠道由 updateOrderPaid 按支付单写回 —— 这也正是微信物流同步**自动跳过**它的依据（R46）
        assertEquals(PayChannelEnum.OFFLINE.getCode(), dbOrder.getPayChannelCode());
        // 支付单号要回填：线上支付是在 createPayOrder 里写的，而 updateOrderPaid **不写**它
        assertEquals(9999L, dbOrder.getPayOrderId());

        // 2. 子表回填了痕迹（FR-204 / FR-205）
        TradeOrderOfflinePaymentDO offlinePayment = offlinePaymentMapper.selectByOrderId(order.getId());
        assertEquals("2026101600012345", offlinePayment.getBankSerialNo());
        assertEquals("南京路桥机械有限公司", offlinePayment.getPayerName());
        assertEquals(249L, offlinePayment.getConfirmUserId());
        assertNotNull(offlinePayment.getConfirmTime());

        // 3. ⚠️ 造支付单时**金额与商户单号必须与订单对齐**（R43）——
        //    对不上 `updateOrderPaid` 会抛 `..._PAY_PRICE_NOT_MATCH`，订单永远进不了待发货
        ArgumentCaptor<PayOrderCreateReqDTO> captor = ArgumentCaptor.forClass(PayOrderCreateReqDTO.class);
        verify(payOrderApi).createPaidOfflineOrder(captor.capture());
        assertEquals(String.valueOf(order.getId()), captor.getValue().getMerchantOrderId());
        assertEquals(order.getPayPrice(), captor.getValue().getPrice());
    }

    @Test
    public void testConfirmOfflinePaid_secondTime_idempotent() {
        TradeOrderDO order = createWaitingOfflineOrder();
        stubPaidOfflinePayOrder(order);
        orderService.confirmOfflinePaid(confirmReqVO(order.getId(), "第一笔的流水号"), 249L);

        // 第二次（含并发）：订单已是「待发货」→ 直接拒，**不会再造一张支付单**（FR-206 / SC-072）
        assertServiceException(() -> orderService.confirmOfflinePaid(
                confirmReqVO(order.getId(), "第二笔的流水号"), 250L), ORDER_CONFIRM_OFFLINE_PAID_FAIL_ALREADY_PAID);

        verify(payOrderApi, times(1)).createPaidOfflineOrder(any()); // 只造过一次
        TradeOrderOfflinePaymentDO offlinePayment = offlinePaymentMapper.selectByOrderId(order.getId());
        assertEquals("第一笔的流水号", offlinePayment.getBankSerialNo()); // 痕迹没被第二次盖掉
        assertEquals(249L, offlinePayment.getConfirmUserId());
    }

    @Test
    public void testConfirmOfflinePaid_notOfflineOrder_fail() {
        // 普通待支付订单：没有"线下收款"这回事，别被误确认成已支付（FR-207）
        TradeOrderDO order = createOrder(TradeOrderStatusEnum.UNPAID.getStatus(),
                PayChannelEnum.WX_LITE.getCode(), 7788L, LocalDateTime.now());

        assertServiceException(() -> orderService.confirmOfflinePaid(
                confirmReqVO(order.getId(), "2026101600012345"), 249L),
                ORDER_CONFIRM_OFFLINE_PAID_FAIL_NOT_OFFLINE_ORDER);

        verify(payOrderApi, never()).createPaidOfflineOrder(any());
        assertEquals(TradeOrderStatusEnum.UNPAID.getStatus(),
                tradeOrderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    public void testConfirmOfflinePaid_canceled_fail() {
        // 已取消的对公单**不能**再确认收款（FR-214）
        TradeOrderDO order = createOrder(TradeOrderStatusEnum.CANCELED.getStatus(), null, null,
                LocalDateTime.now());
        offlinePaymentService.createOfflinePayment(order.getId());

        assertServiceException(() -> orderService.confirmOfflinePaid(
                confirmReqVO(order.getId(), "2026101600012345"), 249L),
                ORDER_CONFIRM_OFFLINE_PAID_FAIL_CANCELED);

        verify(payOrderApi, never()).createPaidOfflineOrder(any());
    }

    /**
     * ⚠️ **线上回归**（2026-10-16 发版当天发现并修）：租户**没有 `trade_config` 那行**时
     * `getTradeConfig()` 返回 **null** —— 而结算页会**无条件**读它。
     *
     * 实测：线上 `trade_config` **只有 tenant_id = 1 的一行**，本项目（租户 162）没有行，
     * 于是「结算」对**每个客户**都 500（`NullPointerException`）。
     * 这个用例就是那次事故的看门人：**没有配置 = 对公转账不开放**，而不是崩掉。
     *
     * 📌 教训：`@MockitoBean` 的默认返回值（null）与线上**真实存在**的"查不到配置"是同一件事，
     * 但**只有把它写进用例**才会被看见 —— 单测里 mock 出来的 null 不会自己变红。
     */
    @Test
    public void testSettlementOrder_tradeConfigMissing_shouldNotThrow() {
        // mock：这个租户还没有交易配置
        when(tradeConfigService.getTradeConfig()).thenReturn(null);
        // mock：价格计算返回一个最小可用的结果（本用例只关心"读配置"这一段）
        TradePriceCalculateRespBO calculateRespBO = new TradePriceCalculateRespBO();
        calculateRespBO.setType(0);
        calculateRespBO.setPrice(new TradePriceCalculateRespBO.Price());
        calculateRespBO.setItems(new ArrayList<>());
        when(tradePriceService.calculateOrderPrice(any())).thenReturn(calculateRespBO);
        when(cartService.getCartList(any(), any())).thenReturn(new ArrayList<>());
        // 准备参数
        AppTradeOrderSettlementReqVO reqVO = new AppTradeOrderSettlementReqVO();
        AppTradeOrderSettlementReqVO.Item item = new AppTradeOrderSettlementReqVO.Item();
        item.setSkuId(100L);
        item.setCount(1);
        reqVO.setItems(List.of(item));

        // 调用：**不许抛异常**
        AppTradeOrderSettlementRespVO respVO = orderService.settlementOrder(88L, reqVO);

        // 没有配置 → 对公转账不开放（默认关），但页面照常能用
        assertNotNull(respVO);
        assertEquals(Boolean.FALSE, respVO.getOfflinePayEnabled());
    }

    @Test
    public void testSettlementOrder_enterpriseSkuForNonAdmin_shouldReject() {
        when(tradeConfigService.getTradeConfig()).thenReturn(null);
        TradePriceCalculateRespBO calculateRespBO = new TradePriceCalculateRespBO();
        calculateRespBO.setType(0);
        calculateRespBO.setPrice(new TradePriceCalculateRespBO.Price());
        TradePriceCalculateRespBO.OrderItem orderItem = new TradePriceCalculateRespBO.OrderItem();
        orderItem.setSkuId(301L);
        orderItem.setCount(1);
        calculateRespBO.setItems(List.of(orderItem));
        when(tradePriceService.calculateOrderPrice(any())).thenReturn(calculateRespBO);
        when(cartService.getCartList(any(), any())).thenReturn(new ArrayList<>());

        ProductSubscriptionPlanRespDTO enterprisePlan = new ProductSubscriptionPlanRespDTO();
        enterprisePlan.setId(11L);
        enterprisePlan.setPeriodMonths(12);
        enterprisePlan.setScope(2);
        enterprisePlan.setEnabled(true);
        when(productSubscriptionPlanApi.getBySkuId(301L)).thenReturn(CommonResult.success(enterprisePlan));
        when(memberOrgApi.getByMemberId(88L)).thenReturn(CommonResult.success(null));

        AppTradeOrderSettlementReqVO reqVO = new AppTradeOrderSettlementReqVO();
        AppTradeOrderSettlementReqVO.Item item = new AppTradeOrderSettlementReqVO.Item();
        item.setSkuId(301L);
        item.setCount(1);
        reqVO.setItems(List.of(item));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> orderService.settlementOrder(88L, reqVO));
        assertEquals(ENTERPRISE_PRODUCT_NOT_AVAILABLE.getCode(), exception.getCode());
    }

    private ProductSubscriptionPlanRespDTO enterprisePlan() {
        ProductSubscriptionPlanRespDTO plan = new ProductSubscriptionPlanRespDTO();
        plan.setId(11L);
        plan.setSpuId(500L);
        plan.setSkuId(301L);
        plan.setPeriodMonths(12);
        plan.setScope(2);
        plan.setEnabled(true);
        return plan;
    }

    private MemberOrgRespDTO enterpriseOrg(int role) {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setRole(role);
        org.setStatus(0);
        return org;
    }

    private AppTradeOrderCreateReqVO enterpriseCreateReq(Integer titleKind) {
        AppTradeOrderSettlementReqVO.Item item = new AppTradeOrderSettlementReqVO.Item();
        item.setSkuId(301L);
        item.setCount(2);
        AppTradeOrderCreateReqVO reqVO = new AppTradeOrderCreateReqVO();
        reqVO.setItems(List.of(item));
        reqVO.setCouponId(0L);
        if (titleKind != null) {
            AppInvoiceApplyBaseVO invoice = new AppInvoiceApplyBaseVO();
            invoice.setType(1);
            invoice.setTitleKind(titleKind);
            invoice.setTitleName("测试企业");
            invoice.setTaxNo("91320104660690975E");
            reqVO.setInvoice(invoice);
        }
        return reqVO;
    }

    private void mockEnterprisePrice() {
        TradePriceCalculateRespBO calculateRespBO = new TradePriceCalculateRespBO();
        calculateRespBO.setType(0);
        calculateRespBO.setDeliveryType(3); // 纯虚拟商品
        TradePriceCalculateRespBO.Price price = new TradePriceCalculateRespBO.Price();
        price.setTotalPrice(198000);
        price.setDiscountPrice(0);
        price.setDeliveryPrice(0);
        price.setCouponPrice(0);
        price.setPointPrice(0);
        price.setVipPrice(0);
        price.setPayPrice(198000);
        calculateRespBO.setPrice(price);
        TradePriceCalculateRespBO.OrderItem item = new TradePriceCalculateRespBO.OrderItem();
        item.setSpuId(500L).setSkuId(301L).setCount(2)
                .setPrice(99000).setDiscountPrice(0).setPayPrice(198000).setSpuName("企业授权");
        calculateRespBO.setItems(List.of(item));
        when(tradePriceService.calculateOrderPrice(any())).thenReturn(calculateRespBO);
        when(cartService.getCartList(any(), any())).thenReturn(new ArrayList<>());
    }

    @Test
    public void testCreateOrder_enterpriseAdminWithCompanyInvoice_snapshotsOrg() {
        when(productSubscriptionPlanApi.getBySkuId(301L)).thenReturn(CommonResult.success(enterprisePlan()));
        when(memberOrgApi.getByMemberId(88L)).thenReturn(CommonResult.success(enterpriseOrg(1)));
        when(tradeNoRedisDAO.generate(any())).thenReturn("NO-ENT");
        when(payOrderApi.createOrder(any())).thenReturn(CommonResult.success(1L));
        mockEnterprisePrice();

        // user_ip / terminal 来自 ServletUtils / WebFrameworkUtils，单测里没有请求上下文
        try (MockedStatic<ServletUtils> servletUtils = mockStatic(ServletUtils.class);
             MockedStatic<WebFrameworkUtils> webFrameworkUtils = mockStatic(WebFrameworkUtils.class)) {
            servletUtils.when(ServletUtils::getClientIP).thenReturn("127.0.0.1");
            webFrameworkUtils.when(WebFrameworkUtils::getTerminal).thenReturn(0);

            TradeOrderDO order = orderService.createOrder(88L, enterpriseCreateReq(1));
            assertEquals(88L, order.getMaxlabelOrgId());
        }
    }

    @Test
    public void testCreateOrder_nonAdminBuyerRejected() {
        when(productSubscriptionPlanApi.getBySkuId(301L)).thenReturn(CommonResult.success(enterprisePlan()));
        when(memberOrgApi.getByMemberId(88L)).thenReturn(CommonResult.success(null));
        mockEnterprisePrice();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> orderService.createOrder(88L, enterpriseCreateReq(1)));
        assertEquals(ENTERPRISE_ORDER_BUYER_NOT_ADMIN.getCode(), exception.getCode());
    }

    @Test
    public void testCreateOrder_personalInvoiceRejected() {
        when(productSubscriptionPlanApi.getBySkuId(301L)).thenReturn(CommonResult.success(enterprisePlan()));
        when(memberOrgApi.getByMemberId(88L)).thenReturn(CommonResult.success(enterpriseOrg(1)));
        mockEnterprisePrice();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> orderService.createOrder(88L, enterpriseCreateReq(2)));
        assertEquals(ENTERPRISE_ORDER_INVOICE_TITLE_REQUIRED.getCode(), exception.getCode());
    }

    // ==================== 超时自动取消：对公单必须被跳过（T481 / FR-212 / SC-075）====================

    @Test
    public void testCancelOrderBySystem_skipOfflineOrder_butCancelNormalOne() {
        LocalDateTime expired = LocalDateTime.now().minusHours(3); // 早过 2h 期限
        TradeOrderDO offlineOrder = createWaitingOfflineOrder();
        TradeOrderDO expiredPatch = new TradeOrderDO();
        expiredPatch.setId(offlineOrder.getId());
        expiredPatch.setCreateTime(expired);
        tradeOrderMapper.updateById(expiredPatch);
        TradeOrderDO normalOrder = createOrder(TradeOrderStatusEnum.UNPAID.getStatus(),
                PayChannelEnum.WX_LITE.getCode(), 7788L, expired);

        int count = orderService.cancelOrderBySystem();

        // 只取消了普通单（SC-075 的两半：**对公单留、普通单照旧被取消**）
        assertEquals(1, count);
        assertEquals(TradeOrderStatusEnum.UNPAID.getStatus(),
                tradeOrderMapper.selectById(offlineOrder.getId()).getStatus());
        assertEquals(TradeOrderStatusEnum.CANCELED.getStatus(),
                tradeOrderMapper.selectById(normalOrder.getId()).getStatus());
        assertEquals(TradeOrderCancelTypeEnum.PAY_TIMEOUT.getType(),
                tradeOrderMapper.selectById(normalOrder.getId()).getCancelType());
        // ⚠️ 对公单的 payOrderId 是 null，**绝不能**被传进 pay RPC（R45 的那句兜底）
        verify(payOrderApi, never()).getOrder(isNull());
    }

    @Test
    public void testCancelOrderBySystem_offlineOrderNotCanceled_evenIfCalledDirectly() {
        // 第二道保护：**直接**调单个订单的自动取消（比如有人手工触发），对公单照样不被砍
        TradeOrderDO offlineOrder = createWaitingOfflineOrder();

        orderService.cancelOrderBySystem(offlineOrder);

        assertEquals(TradeOrderStatusEnum.UNPAID.getStatus(),
                tradeOrderMapper.selectById(offlineOrder.getId()).getStatus());
        verify(payOrderApi, never()).getOrder(isNull());
    }

    // ==================== 后台取消订单（T486 / FR-213）====================

    @Test
    public void testCancelOrderByAdmin_success() {
        TradeOrderDO order = createWaitingOfflineOrder();
        TradeOrderCancelReqVO reqVO = new TradeOrderCancelReqVO();
        reqVO.setId(order.getId());
        reqVO.setReason("客户已放弃打款");

        orderService.cancelOrderByAdmin(reqVO);

        TradeOrderDO dbOrder = tradeOrderMapper.selectById(order.getId());
        assertEquals(TradeOrderStatusEnum.CANCELED.getStatus(), dbOrder.getStatus());
        // 取消类型独立一个取值：**"谁清的"要看得出来**，不能混进「超时未支付」
        assertEquals(TradeOrderCancelTypeEnum.ADMIN_CANCEL.getType(), dbOrder.getCancelType());
        // 留痕：原因写进后台可见的备注
        assertEquals("客户已放弃打款", dbOrder.getRemark());
    }

    @Test
    public void testCancelOrderByAdmin_paidOrder_fail() {
        // 已支付的（含已确认收款的）MUST 走**退款**，不能用取消 —— 取消不改金额，账对不上（FR-213）
        TradeOrderDO order = createOrder(TradeOrderStatusEnum.UNDELIVERED.getStatus(),
                PayChannelEnum.OFFLINE.getCode(), 9999L, LocalDateTime.now());
        offlinePaymentService.createOfflinePayment(order.getId());
        TradeOrderCancelReqVO reqVO = new TradeOrderCancelReqVO();
        reqVO.setId(order.getId());

        assertServiceException(() -> orderService.cancelOrderByAdmin(reqVO), ORDER_CANCEL_FAIL_STATUS_NOT_UNPAID);
        assertEquals(TradeOrderStatusEnum.UNDELIVERED.getStatus(),
                tradeOrderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    public void testCancelOrderByAdmin_confirmedOfflineOrder_canStillBeCanceledBeforeReceipt() {
        // 「关掉对公转账」后**已经产生的待收款单不受影响**：仍能取消（FR-210 的下半句）——
        // 这条不依赖任何开关，写出来是为了钉住"确认收款与取消互不牵连"
        TradeOrderDO order = createWaitingOfflineOrder();
        TradeOrderCancelReqVO reqVO = new TradeOrderCancelReqVO();
        reqVO.setId(order.getId());

        orderService.cancelOrderByAdmin(reqVO);

        assertEquals(TradeOrderStatusEnum.CANCELED.getStatus(),
                tradeOrderMapper.selectById(order.getId()).getStatus());
        // 取消之后**不能再确认收款**（FR-214）
        assertServiceException(() -> orderService.confirmOfflinePaid(
                confirmReqVO(order.getId(), "2026101600012345"), 249L),
                ORDER_CONFIRM_OFFLINE_PAID_FAIL_CANCELED);
    }

    // ==================== 010 追加：发货地址 ====================

    private TradeOrderDeliveryReqVO deliveryReqVO(TradeOrderDO order) {
        return new TradeOrderDeliveryReqVO().setId(order.getId())
                .setLogisticsId(1L).setLogisticsNo("SF1234567890");
    }

    @Test
    public void testDeliveryOrder_expressWithoutFromAddress_rejected() {
        TradeOrderDO order = createOrder(TradeOrderStatusEnum.UNDELIVERED.getStatus(),
                null, null, LocalDateTime.now());

        assertServiceException(() -> orderService.deliveryOrder(deliveryReqVO(order)),
                ORDER_DELIVERY_FROM_ADDRESS_REQUIRED);

        // 订单仍是待发货
        assertEquals(TradeOrderStatusEnum.UNDELIVERED.getStatus(),
                tradeOrderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    public void testDeliveryOrder_expressWithFromAddress_saved() {
        TradeOrderDO order = createOrder(TradeOrderStatusEnum.UNDELIVERED.getStatus(),
                null, null, LocalDateTime.now());
        TradeOrderDeliveryReqVO reqVO = deliveryReqVO(order)
                .setDeliveryFromAreaId(110108)
                .setDeliveryFromAddress("西北旺亮甲店永丰中路100号北京人才教育基地红楼107");

        orderService.deliveryOrder(reqVO);

        TradeOrderDO updated = tradeOrderMapper.selectById(order.getId());
        assertEquals(TradeOrderStatusEnum.DELIVERED.getStatus(), updated.getStatus());
        assertEquals(110108, updated.getDeliveryFromAreaId());
        assertEquals("西北旺亮甲店永丰中路100号北京人才教育基地红楼107", updated.getDeliveryFromAddress());
    }

}
