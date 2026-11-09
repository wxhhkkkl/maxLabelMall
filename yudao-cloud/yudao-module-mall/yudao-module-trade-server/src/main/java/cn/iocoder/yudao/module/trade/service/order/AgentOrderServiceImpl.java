package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderSubmitReqDTO;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderSubmitRespDTO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderPayReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderPayRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderPayStatusRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.AdminAgentOrderSettlementReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderCreateRespVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

/**
 * 代客下单 Service 实现（017）
 */
@Service
@Validated
public class AgentOrderServiceImpl implements AgentOrderService {

    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private ProductSkuApi productSkuApi;
    @Resource
    private ProductSpuApi productSpuApi;
    @Resource
    private PayOrderApi payOrderApi;

    @Override
    public AppTradeOrderSettlementRespVO settlement(AdminAgentOrderSettlementReqVO reqVO) {
        validateCustomer(reqVO.getUserId());
        validateProducts(reqVO.getItems().stream().map(AdminAgentOrderSettlementReqVO.Item::getSkuId)
                .collect(Collectors.toList()));
        // ⚠️ 预览**不校验地址**：没有默认地址时仍要返回 deliveryType/价格，
        //    否则页面拿不到 settlement，就永远不渲染「收货地址」卡片，运营无处可选。
        //    地址校验放到 create（真正提交）时做。
        return tradeOrderUpdateService.settlementOrder(reqVO.getUserId(), buildSettlementReq(reqVO));
    }

    @Override
    public TradeOrderCreateRespVO create(Long operatorUserId, AdminAgentOrderCreateReqVO reqVO) {
        validateCustomer(reqVO.getUserId());
        validateProducts(reqVO.getItems().stream().map(AdminAgentOrderCreateReqVO.Item::getSkuId)
                .collect(Collectors.toList()));
        // 1. 先算价，拿到服务端判定的配送方式与地址
        AppTradeOrderSettlementRespVO settlement = tradeOrderUpdateService.settlementOrder(
                reqVO.getUserId(), buildSettlementReq(reqVO));
        validateAddress(settlement);
        // 2. 构建 C 端创建请求：券/积分强制关闭，金额完全由服务端重算
        AppTradeOrderCreateReqVO createReqVO = new AppTradeOrderCreateReqVO();
        createReqVO.setItems(buildCreateItems(reqVO));
        createReqVO.setCouponId(null);
        createReqVO.setPointStatus(false);
        createReqVO.setDeliveryType(settlement.getDeliveryType());
        createReqVO.setAddressId(settlement.getAddress() != null ? settlement.getAddress().getId() : null);
        createReqVO.setRemark(reqVO.getUserRemark());
        createReqVO.setPayChannelCode(reqVO.getPayChannelCode());
        // 3. 创建订单（写 agent_user_id + 来源 40 + ADMIN_AGENT_CREATE 日志）
        TradeOrderDO order = tradeOrderUpdateService.createOrderByAdmin(
                operatorUserId, reqVO.getUserId(), createReqVO);
        return new TradeOrderCreateRespVO().setOrderId(order.getId())
                .setPayOrderId(order.getPayOrderId())
                .setPayPrice(order.getPayPrice())
                .setOrderStatus(order.getStatus());
    }

    @Override
    public AdminAgentOrderPayRespVO pay(AdminAgentOrderPayReqVO reqVO) {
        // 1. 校验渠道白名单：只允许真实扫码，绝不暴露 mock / wallet
        String channelCode = reqVO.getChannelCode();
        if (!Objects.equals(PayChannelEnum.WX_NATIVE.getCode(), channelCode)
                && !Objects.equals(PayChannelEnum.ALIPAY_PC.getCode(), channelCode)) {
            throw exception(AGENT_ORDER_CHANNEL_NOT_SUPPORTED);
        }
        // 2. 校验订单：必须是代客订单、待支付、且存在支付单
        TradeOrderDO order = tradeOrderQueryService.getOrder(reqVO.getOrderId());
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        if (order.getAgentUserId() == null) {
            throw exception(AGENT_ORDER_PAY_FAIL_NOT_AGENT_ORDER);
        }
        if (!TradeOrderStatusEnum.isUnpaid(order.getStatus()) || Boolean.TRUE.equals(order.getPayStatus())
                || order.getPayOrderId() == null) {
            throw exception(AGENT_ORDER_PAY_FAIL_STATUS);
        }
        // 3. 调支付模块提交（服务端收口，前端不直连 /pay/order/submit）
        PayOrderSubmitReqDTO submitReqDTO = new PayOrderSubmitReqDTO();
        submitReqDTO.setId(order.getPayOrderId());
        submitReqDTO.setChannelCode(channelCode);
        PayOrderSubmitRespDTO respDTO = payOrderApi.submitOrder(submitReqDTO, ServletUtils.getClientIP())
                .getCheckedData();
        return new AdminAgentOrderPayRespVO().setStatus(respDTO.getStatus())
                .setDisplayMode(respDTO.getDisplayMode())
                .setDisplayContent(respDTO.getDisplayContent());
    }

    @Override
    public AdminAgentOrderPayStatusRespVO getPayStatus(Long orderId) {
        TradeOrderDO order = tradeOrderQueryService.getOrder(orderId);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 与 agent-pay 一致：只允许查询代客订单的支付状态
        if (order.getAgentUserId() == null) {
            throw exception(AGENT_ORDER_PAY_FAIL_NOT_AGENT_ORDER);
        }
        // 支付单已成功但订单还没更新：兜底同步一次（回调延迟 / 页面轮询先到）
        if (order.getPayOrderId() != null && !Boolean.TRUE.equals(order.getPayStatus())) {
            PayOrderRespDTO payOrder = payOrderApi.getOrder(order.getPayOrderId()).getCheckedData();
            if (payOrder != null && PayOrderStatusEnum.isSuccess(payOrder.getStatus())) {
                tradeOrderUpdateService.syncOrderPayStatusQuietly(orderId, order.getPayOrderId());
                order = tradeOrderQueryService.getOrder(orderId);
            }
        }
        return new AdminAgentOrderPayStatusRespVO().setOrderId(orderId)
                .setOrderStatus(order.getStatus())
                .setPayStatus(order.getPayStatus())
                .setPayOrderId(order.getPayOrderId());
    }

    // ==================== 校验 ====================

    private void validateCustomer(Long userId) {
        CommonResult<MemberUserRespDTO> result = memberUserApi.getUser(userId);
        if (result.isError() || result.getData() == null) {
            throw exception(AGENT_ORDER_CUSTOMER_NOT_FOUND);
        }
        if (!CommonStatusEnum.ENABLE.getStatus().equals(result.getData().getStatus())) {
            throw exception(AGENT_ORDER_CUSTOMER_DISABLED);
        }
    }

    private void validateProducts(List<Long> skuIds) {
        // 隐藏定制 SKU：显式拒绝，避免落到通用「价格计算不正确」
        if (skuIds.contains(CustomizationConstants.HIDDEN_SKU_ID)) {
            throw exception(AGENT_ORDER_CUSTOMIZATION_NOT_SUPPORTED);
        }
        Map<Long, ProductSkuRespDTO> skuMap = productSkuApi.getSkuMap(skuIds);
        Set<Long> spuIds = skuMap.values().stream().map(ProductSkuRespDTO::getSpuId)
                .collect(Collectors.toSet());
        if (spuIds.contains(CustomizationConstants.HIDDEN_SPU_ID)) {
            throw exception(AGENT_ORDER_CUSTOMIZATION_NOT_SUPPORTED);
        }
        Map<Long, ProductSpuRespDTO> spuMap = productSpuApi.getSpuMap(spuIds);
        for (ProductSpuRespDTO spu : spuMap.values()) {
            List<Integer> types = spu.getDeliveryTypes();
            // 仅自提：既不含快递、也不含虚拟（空值按快递处理，与 resolveDeliveryType 的保守口径一致）
            if (types != null && types.contains(DeliveryTypeEnum.PICK_UP.getType())
                    && !types.contains(DeliveryTypeEnum.EXPRESS.getType())
                    && !types.contains(DeliveryTypeEnum.VIRTUAL.getType())) {
                throw exception(AGENT_ORDER_PICKUP_NOT_SUPPORTED);
            }
        }
    }

    private void validateAddress(AppTradeOrderSettlementRespVO respVO) {
        if (Objects.equals(DeliveryTypeEnum.EXPRESS.getType(), respVO.getDeliveryType())
                && respVO.getAddress() == null) {
            throw exception(AGENT_ORDER_ADDRESS_REQUIRED);
        }
    }

    // ==================== 请求构建 ====================

    private AppTradeOrderSettlementReqVO buildSettlementReq(AdminAgentOrderSettlementReqVO reqVO) {
        AppTradeOrderSettlementReqVO settlementReqVO = new AppTradeOrderSettlementReqVO();
        settlementReqVO.setItems(reqVO.getItems().stream().map(item -> {
            AppTradeOrderSettlementReqVO.Item target = new AppTradeOrderSettlementReqVO.Item();
            target.setSkuId(item.getSkuId());
            target.setCount(item.getCount());
            return target;
        }).collect(Collectors.toList()));
        settlementReqVO.setCouponId(null);
        settlementReqVO.setPointStatus(false);
        settlementReqVO.setAddressId(reqVO.getAddressId());
        return settlementReqVO;
    }

    private AppTradeOrderSettlementReqVO buildSettlementReq(AdminAgentOrderCreateReqVO reqVO) {
        AppTradeOrderSettlementReqVO settlementReqVO = new AppTradeOrderSettlementReqVO();
        settlementReqVO.setItems(buildCreateItems(reqVO));
        settlementReqVO.setCouponId(null);
        settlementReqVO.setPointStatus(false);
        settlementReqVO.setAddressId(reqVO.getAddressId());
        return settlementReqVO;
    }

    private List<AppTradeOrderSettlementReqVO.Item> buildCreateItems(AdminAgentOrderCreateReqVO reqVO) {
        return reqVO.getItems().stream().map(item -> {
            AppTradeOrderSettlementReqVO.Item target = new AppTradeOrderSettlementReqVO.Item();
            target.setSkuId(item.getSkuId());
            target.setCount(item.getCount());
            return target;
        }).collect(Collectors.toList());
    }

}
