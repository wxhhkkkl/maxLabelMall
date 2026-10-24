package cn.iocoder.yudao.module.trade.controller.app.order;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.*;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.item.AppTradeOrderItemCommentCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.item.AppTradeOrderItemRespVO;
import cn.iocoder.yudao.module.trade.convert.invoice.InvoiceConvert;
import cn.iocoder.yudao.module.trade.convert.order.TradeOrderConvert;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeOrderInvoiceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.offline.TradeOrderOfflinePaymentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.aftersale.AfterSaleService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.yudao.module.trade.service.invoice.TradeOrderInvoiceService;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import cn.iocoder.yudao.module.trade.service.offline.TradeOrderOfflinePaymentService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.yudao.module.trade.service.price.TradePriceService;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 交易订单")
@RestController
@RequestMapping("/trade/order")
@Validated
@Slf4j
public class AppTradeOrderController {

    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private DeliveryExpressService deliveryExpressService;
    @Resource
    private AfterSaleService afterSaleService;
    @Resource
    private TradePriceService priceService;

    @Resource
    private TradeOrderProperties tradeOrderProperties;
    @Resource
    private TradeOrderInvoiceService tradeOrderInvoiceService;
    @Resource
    private TradeConfigService tradeConfigService;
    @Resource
    private TradeOrderOfflinePaymentService tradeOrderOfflinePaymentService;

    @GetMapping("/settlement")
    @Operation(summary = "获得订单结算信息")
    public CommonResult<AppTradeOrderSettlementRespVO> settlementOrder(@Valid AppTradeOrderSettlementReqVO settlementReqVO) {
        return success(tradeOrderUpdateService.settlementOrder(getLoginUserId(), settlementReqVO));
    }

    @GetMapping("/settlement-product")
    @Operation(summary = "获得商品结算信息", description = "用于商品列表、商品详情，获得参与活动后的价格信息")
    @Parameter(name = "spuIds", description = "商品 SPU 编号数组")
    @PermitAll
    public CommonResult<List<AppTradeProductSettlementRespVO>> settlementProduct(@RequestParam("spuIds") List<Long> spuIds) {
        return success(priceService.calculateProductPrice(getLoginUserId(), spuIds));
    }

    @PostMapping("/create")
    @Operation(summary = "创建订单")
    public CommonResult<AppTradeOrderCreateRespVO> createOrder(@Valid @RequestBody AppTradeOrderCreateReqVO createReqVO) {
        TradeOrderDO order = tradeOrderUpdateService.createOrder(getLoginUserId(), createReqVO);
        return success(new AppTradeOrderCreateRespVO().setId(order.getId()).setPayOrderId(order.getPayOrderId()));
    }

    @PostMapping("/update-paid")
    @Operation(summary = "更新订单为已支付") // 由 pay-module 支付服务，进行回调，可见 PayNotifyJob
    @PermitAll
    public CommonResult<Boolean> updateOrderPaid(@RequestBody PayOrderNotifyReqDTO notifyReqDTO) {
        tradeOrderUpdateService.updateOrderPaid(Long.valueOf(notifyReqDTO.getMerchantOrderId()),
                notifyReqDTO.getPayOrderId());
        return success(true);
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得交易订单")
    @Parameters({
            @Parameter(name = "id", description = "交易订单编号"),
            @Parameter(name = "sync", description = "是否同步支付状态", example = "true")
    })
    public CommonResult<AppTradeOrderDetailRespVO> getOrderDetail(@RequestParam("id") Long id,
                                                                  @RequestParam(value = "sync", required = false) Boolean sync) {
        // 1.1 查询订单
        TradeOrderDO order = tradeOrderQueryService.getOrder(getLoginUserId(), id);
        if (order == null) {
            return success(null);
        }
        // 1.2 sync 仅在等待支付
        if (Boolean.TRUE.equals(sync)
                && TradeOrderStatusEnum.isUnpaid(order.getStatus()) && !order.getPayStatus()) {
            tradeOrderUpdateService.syncOrderPayStatusQuietly(order.getId(), order.getPayOrderId());
            // 重新查询，因为同步后，可能会有变化
            order = tradeOrderQueryService.getOrder(id);
        }

        // 2.1 查询订单项
        List<TradeOrderItemDO> orderItems = tradeOrderQueryService.getOrderItemListByOrderId(order.getId());
        // 2.2 查询物流公司
        DeliveryExpressDO express = order.getLogisticsId() != null && order.getLogisticsId() > 0 ?
                deliveryExpressService.getDeliveryExpress(order.getLogisticsId()) : null;
        // 2.3 最终组合
        AppTradeOrderDetailRespVO respVO = TradeOrderConvert.INSTANCE.convert02(order, orderItems, tradeOrderProperties, express);
        // 2.4 开票信息（006）：**没申请时是 null** —— 前端据此区分"没申请"与"申请了"（FR-176）。
        //     不塞进 convert02，是为了不碰那个被多处共用的 MapStruct 方法签名
        if (respVO != null) {
            respVO.setInvoice(InvoiceConvert.convertToAppRespVO(
                    tradeOrderInvoiceService.getInvoiceByOrderId(order.getId())));
            // 2.5 对公转账（008）：**不是对公单时是 null** —— 前端据此渲染「待收款」区块。
            //     ⚠️ 收款账户明细只在这里给（不下发到结算接口），因为客户是**下单之后**才需要
            //     照着它去银行打款的。
            respVO.setOfflinePay(buildOfflinePay(order));
        }
        return success(respVO);
    }

    /**
     * 组装订单详情里的**对公转账**信息（008）
     *
     * @return **不是对公单时返回 null**（前端据此"整块不渲染"）
     */
    private AppOrderOfflinePayRespVO buildOfflinePay(TradeOrderDO order) {
        TradeOrderOfflinePaymentDO offlinePayment =
                tradeOrderOfflinePaymentService.getOfflinePaymentByOrderId(order.getId());
        if (offlinePayment == null) {
            return null; // 不是对公单
        }
        // ⚠️ 没有 trade_config 行时 getTradeConfig() 返回 null（线上租户 162 实测如此）——
        //    照着解引用会让**订单详情 500**，而不是"没配账户"这种可接受的降级
        TradeConfigDO tradeConfig = ObjUtil.defaultIfNull(tradeConfigService.getTradeConfig(), new TradeConfigDO());
        // 「待收款」= 还有没确认 **且 订单还没付** —— 两个都要判：
        // 只判"没确认"的话，客户已经付款（或订单已取消）时还会显示"请打款"
        boolean waiting = offlinePayment.getConfirmTime() == null
                && !Boolean.TRUE.equals(order.getPayStatus());
        return new AppOrderOfflinePayRespVO()
                .setWaiting(waiting)
                .setAccountName(tradeConfig.getOfflinePayAccountName())
                .setAccountNo(tradeConfig.getOfflinePayAccountNo())
                .setBankName(tradeConfig.getOfflinePayBankName())
                .setTip(tradeConfig.getOfflinePayTip());
    }

    @GetMapping("/get-express-track-list")
    @Operation(summary = "获得交易订单的物流轨迹")
    @Parameter(name = "id", description = "交易订单编号")
    public CommonResult<List<AppOrderExpressTrackRespDTO>> getOrderExpressTrackList(@RequestParam("id") Long id) {
        return success(TradeOrderConvert.INSTANCE.convertList02(
                tradeOrderQueryService.getExpressTrackList(id, getLoginUserId())));
    }

    @GetMapping("/get-express-map-url")
    @Operation(summary = "获得交易订单的物流地图地址")
    @Parameter(name = "id", description = "交易订单编号")
    public CommonResult<String> getExpressMapUrl(@RequestParam("id") Long id) {
        return success(tradeOrderQueryService.getExpressMapUrl(id, getLoginUserId()));
    }

    @GetMapping("/page")
    @Operation(summary = "获得交易订单分页")
    public CommonResult<PageResult<AppTradeOrderPageItemRespVO>> getOrderPage(AppTradeOrderPageReqVO reqVO) {
        // 查询订单
        PageResult<TradeOrderDO> pageResult = tradeOrderQueryService.getOrderPage(getLoginUserId(), reqVO);
        // 查询订单项
        List<TradeOrderItemDO> orderItems = tradeOrderQueryService.getOrderItemListByOrderId(
                convertSet(pageResult.getList(), TradeOrderDO::getId));
        // 最终组合
        PageResult<AppTradeOrderPageItemRespVO> page = TradeOrderConvert.INSTANCE.convertPage02(pageResult, orderItems);
        // 开票状态 + 票种（006 / FR-178；011）：**一次 IN 批量查**，不许逐笔查（一页 12 条会变成 13 次查询）。
        // 没申请的订单拿不到值 → 字段保持 null → 列表上什么都不显示
        if (page != null && CollUtil.isNotEmpty(page.getList())) {
            List<TradeOrderInvoiceDO> invoices = tradeOrderInvoiceService.getInvoiceListByOrderIds(
                    convertSet(page.getList(), AppTradeOrderPageItemRespVO::getId));
            Map<Long, Integer> invoiceStatusMap = convertMap(invoices,
                    TradeOrderInvoiceDO::getOrderId, TradeOrderInvoiceDO::getStatus);
            Map<Long, Integer> invoiceTypeMap = convertMap(invoices,
                    TradeOrderInvoiceDO::getOrderId, TradeOrderInvoiceDO::getType);
            page.getList().forEach(item -> {
                item.setInvoiceStatus(invoiceStatusMap.get(item.getId()));
                item.setInvoiceType(invoiceTypeMap.get(item.getId()));
            });

            // 对公转账「待收款」（008 / FR-200）：同样**一次 IN 批量查**。
            // 列表上要把它显示成「待收款」、并且**去掉「立即支付」入口**（对公单在客户侧
            // 没有任何在线支付入口，FR-201）。
            // 「是不是对公单且没确认」由 service 判（只看它自己那张表）；
            // 「订单是否还付着」在这里结合订单状态判 —— 与订单详情的口径一致。
            Set<Long> waitingOrderIds = tradeOrderOfflinePaymentService.getWaitingReceiptOrderIds(
                    convertSet(page.getList(), AppTradeOrderPageItemRespVO::getId));
            page.getList().forEach(item -> item.setOfflineWaiting(
                    waitingOrderIds.contains(item.getId())
                            && TradeOrderStatusEnum.isUnpaid(item.getStatus())));
        }
        return success(page);
    }

    @GetMapping("/get-count")
    @Operation(summary = "获得交易订单数量")
    public CommonResult<Map<String, Long>> getOrderCount() {
        Map<String, Long> orderCount = Maps.newLinkedHashMapWithExpectedSize(5);
        // 全部
        orderCount.put("allCount", tradeOrderQueryService.getOrderCount(getLoginUserId(), null, null));
        // 待付款（未支付）
        orderCount.put("unpaidCount", tradeOrderQueryService.getOrderCount(getLoginUserId(),
                TradeOrderStatusEnum.UNPAID.getStatus(), null));
        // 待发货
        orderCount.put("undeliveredCount", tradeOrderQueryService.getOrderCount(getLoginUserId(),
                TradeOrderStatusEnum.UNDELIVERED.getStatus(), null));
        // 待收货
        orderCount.put("deliveredCount", tradeOrderQueryService.getOrderCount(getLoginUserId(),
                TradeOrderStatusEnum.DELIVERED.getStatus(), null));
        // 待评价
        orderCount.put("uncommentedCount", tradeOrderQueryService.getOrderCount(getLoginUserId(),
                TradeOrderStatusEnum.COMPLETED.getStatus(), false));
        // 售后数量
        orderCount.put("afterSaleCount", afterSaleService.getApplyingAfterSaleCount(getLoginUserId()));
        return success(orderCount);
    }

    @PutMapping("/receive")
    @Operation(summary = "确认交易订单收货")
    @Parameter(name = "id", description = "交易订单编号")
    public CommonResult<Boolean> receiveOrder(@RequestParam("id") Long id) {
        tradeOrderUpdateService.receiveOrderByMember(getLoginUserId(), id);
        return success(true);
    }

    @DeleteMapping("/cancel")
    @Operation(summary = "取消交易订单")
    @Parameter(name = "id", description = "交易订单编号")
    public CommonResult<Boolean> cancelOrder(@RequestParam("id") Long id) {
        tradeOrderUpdateService.cancelOrderByMember(getLoginUserId(), id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除交易订单")
    @Parameter(name = "id", description = "交易订单编号")
    public CommonResult<Boolean> deleteOrder(@RequestParam("id") Long id) {
        tradeOrderUpdateService.deleteOrder(getLoginUserId(), id);
        return success(true);
    }

    // ========== 订单项 ==========

    @GetMapping("/item/get")
    @Operation(summary = "获得交易订单项")
    @Parameter(name = "id", description = "交易订单项编号")
    public CommonResult<AppTradeOrderItemRespVO> getOrderItem(@RequestParam("id") Long id) {
        TradeOrderItemDO item = tradeOrderQueryService.getOrderItem(getLoginUserId(), id);
        return success(TradeOrderConvert.INSTANCE.convert03(item));
    }

    @PostMapping("/item/create-comment")
    @Operation(summary = "创建交易订单项的评价")
    public CommonResult<Long> createOrderItemComment(@RequestBody AppTradeOrderItemCommentCreateReqVO createReqVO) {
        return success(tradeOrderUpdateService.createOrderItemCommentByMember(getLoginUserId(), createReqVO));
    }

}
