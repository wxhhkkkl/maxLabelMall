package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.api.refund.PayRefundApi;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceRepairOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceRepairRequestMapper;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceActionConstants;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceOperatorTypeEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderPayStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTargetTypeEnum;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceOfflinePayInfoBO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceLogRecordConstants.*;

/**
 * 客户服务 - 维修订单 Service 实现（021 / US72）
 *
 * 跨聚合编排统一放在本 service：依赖维修申请 service / mapper，反向不依赖。
 */
@Service
public class CustomerServiceRepairOrderServiceImpl implements CustomerServiceRepairOrderService {

    @Resource
    private CustomerServiceRepairOrderMapper customerServiceRepairOrderMapper;
    @Resource
    private CustomerServiceRepairRequestMapper customerServiceRepairRequestMapper;
    @Resource
    private CustomerServiceRepairRequestService customerServiceRepairRequestService;
    @Resource
    private CustomerServiceLogService customerServiceLogService;
    @Resource
    private PayOrderApi payOrderApi;
    @Resource
    private PayRefundApi payRefundApi;
    @Resource
    private TradeOrderProperties tradeOrderProperties;
    @Resource
    private TradeConfigService tradeConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CUSTOMER_SERVICE_TYPE, subType = REPAIR_ORDER_CREATE_SUB_TYPE,
            success = REPAIR_ORDER_CREATE_SUCCESS, bizNo = "{{#repairRequestId}}")
    public Long createOrder(Long repairRequestId, Integer repairFee, Integer returnFreight,
                            String feeRemark, Long adminUserId) {
        if (repairFee == null || repairFee < 0 || returnFreight == null || returnFreight < 0
                || StrUtil.isBlank(feeRemark) || feeRemark.length() > 500) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_INVALID);
        }
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestService.getRepair(repairRequestId);
        // 先判「已有订单」：重复开单的明确提示优先于笼统的状态不允许
        if (customerServiceRepairOrderMapper.selectByRepairRequestId(repairRequestId) != null) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_EXISTS);
        }
        if (!CustomerServiceRepairStatusEnum.PENDING_CONTACT.getStatus().equals(request.getStatus())
                && !CustomerServiceRepairStatusEnum.CONTACTED.getStatus().equals(request.getStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_STATUS_INVALID);
        }
        int payable = repairFee + returnFreight;
        CustomerServiceRepairOrderDO order = CustomerServiceRepairOrderDO.builder()
                .repairRequestId(repairRequestId)
                .memberId(request.getMemberId())
                .repairFee(repairFee)
                .returnFreight(returnFreight)
                .payableAmount(payable)
                .feeRemark(feeRemark.trim())
                .payStatus(payable == 0
                        ? CustomerServiceRepairOrderPayStatusEnum.NO_PAY.getStatus()
                        : CustomerServiceRepairOrderPayStatusEnum.UNPAID.getStatus())
                .creatorId(adminUserId)
                .build();
        try {
            customerServiceRepairOrderMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            // 并发开单：唯一键兜底，只有一笔成功
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_EXISTS);
        }

        updateRepairStatus(repairRequestId, CustomerServiceRepairStatusEnum.ORDERED.getStatus());
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), repairRequestId,
                CustomerServiceActionConstants.ORDER_CREATE, request.getStatus(),
                CustomerServiceRepairStatusEnum.ORDERED.getStatus(),
                "已生成维修订单，应付 " + payable + " 分",
                CustomerServiceOperatorTypeEnum.ADMIN.getType(), adminUserId);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CUSTOMER_SERVICE_TYPE, subType = REPAIR_CONTACT_SUB_TYPE,
            success = REPAIR_CONTACT_SUCCESS, bizNo = "{{#repairRequestId}}")
    public void recordContact(Long repairRequestId, String contactResult, Long adminUserId) {
        if (StrUtil.isBlank(contactResult) || contactResult.length() > 500) {
            throw exception(CUSTOMER_SERVICE_REPAIR_CONTACT_REQUIRED);
        }
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestService.getRepair(repairRequestId);
        if (!CustomerServiceRepairStatusEnum.PENDING_CONTACT.getStatus().equals(request.getStatus())
                && !CustomerServiceRepairStatusEnum.CONTACTED.getStatus().equals(request.getStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_STATUS_INVALID);
        }
        CustomerServiceRepairRequestDO update = new CustomerServiceRepairRequestDO();
        update.setId(repairRequestId);
        update.setStatus(CustomerServiceRepairStatusEnum.CONTACTED.getStatus());
        update.setContactTime(LocalDateTime.now());
        update.setContactResult(contactResult.trim());
        customerServiceRepairRequestMapper.updateById(update);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), repairRequestId,
                CustomerServiceActionConstants.CONTACT, request.getStatus(),
                CustomerServiceRepairStatusEnum.CONTACTED.getStatus(), contactResult.trim(),
                CustomerServiceOperatorTypeEnum.ADMIN.getType(), adminUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CUSTOMER_SERVICE_TYPE, subType = REPAIR_CANCEL_SUB_TYPE,
            success = REPAIR_CANCEL_SUCCESS, bizNo = "{{#repairRequestId}}")
    public void cancel(Long repairRequestId, String reason, Long adminUserId) {
        if (StrUtil.isBlank(reason) || reason.length() > 255) {
            throw exception(CUSTOMER_SERVICE_REPAIR_CANCEL_REASON_REQUIRED);
        }
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestService.getRepair(repairRequestId);
        if (CustomerServiceRepairStatusEnum.COMPLETED.getStatus().equals(request.getStatus())
                || CustomerServiceRepairStatusEnum.CANCELED.getStatus().equals(request.getStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_STATUS_INVALID);
        }
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderMapper.selectByRepairRequestId(repairRequestId);
        if (order != null) {
            cancelByRequest(order);
        }
        CustomerServiceRepairRequestDO update = new CustomerServiceRepairRequestDO();
        update.setId(repairRequestId);
        update.setStatus(CustomerServiceRepairStatusEnum.CANCELED.getStatus());
        update.setCancelReason(reason.trim());
        customerServiceRepairRequestMapper.updateById(update);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), repairRequestId,
                CustomerServiceActionConstants.CANCEL, request.getStatus(),
                CustomerServiceRepairStatusEnum.CANCELED.getStatus(), reason.trim(),
                CustomerServiceOperatorTypeEnum.ADMIN.getType(), adminUserId);
    }

    @Override
    public CustomerServiceRepairOrderDO getByRepairRequestId(Long repairRequestId) {
        return customerServiceRepairOrderMapper.selectByRepairRequestId(repairRequestId);
    }

    @Override
    public CustomerServiceRepairOrderDO getById(Long id) {
        return customerServiceRepairOrderMapper.selectById(id);
    }

    @Override
    public List<CustomerServiceRepairOrderDO> getListByRepairRequestIds(Collection<Long> repairRequestIds) {
        if (repairRequestIds == null || repairRequestIds.isEmpty()) {
            return Collections.emptyList();
        }
        return customerServiceRepairOrderMapper.selectListByRepairRequestIds(repairRequestIds);
    }

    // ==================== 支付（US73） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long payOnline(Long orderId, Long memberId) {
        CustomerServiceRepairOrderDO order = validateOrder(orderId, memberId);
        if (!CustomerServiceRepairOrderPayStatusEnum.UNPAID.getStatus().equals(order.getPayStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_PAYABLE);
        }
        if (order.getPayOrderId() != null) {
            return order.getPayOrderId();
        }
        Long payOrderId = payOrderApi.createOrder(buildPayOrderReq(order)).getCheckedData();
        CustomerServiceRepairOrderDO update = new CustomerServiceRepairOrderDO();
        update.setId(orderId);
        update.setPayOrderId(payOrderId);
        customerServiceRepairOrderMapper.updateById(update);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_ORDER.getType(), orderId,
                CustomerServiceActionConstants.PAY_ONLINE, null, order.getPayStatus(),
                "发起在线支付", CustomerServiceOperatorTypeEnum.MEMBER.getType(), memberId);
        return payOrderId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerServiceOfflinePayInfoBO chooseOffline(Long orderId, Long memberId) {
        CustomerServiceRepairOrderDO order = validateOrder(orderId, memberId);
        TradeConfigDO config = tradeConfigService.getTradeConfig();
        if (config == null || !Boolean.TRUE.equals(config.getOfflinePayEnabled())) {
            throw exception(CUSTOMER_SERVICE_OFFLINE_NOT_ALLOWED);
        }
        // 已选择对公转账 → 幂等返回收款信息，方便客户关闭弹层后再次查看
        if (CustomerServiceRepairOrderPayStatusEnum.WAITING_CONFIRM.getStatus().equals(order.getPayStatus())) {
            return buildOfflineInfo(config);
        }
        if (!CustomerServiceRepairOrderPayStatusEnum.UNPAID.getStatus().equals(order.getPayStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_PAYABLE);
        }
        CustomerServiceRepairOrderDO update = new CustomerServiceRepairOrderDO();
        update.setId(orderId);
        update.setPayStatus(CustomerServiceRepairOrderPayStatusEnum.WAITING_CONFIRM.getStatus());
        update.setPayChannelCode("offline");
        customerServiceRepairOrderMapper.updateById(update);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_ORDER.getType(), orderId,
                CustomerServiceActionConstants.PAY_OFFLINE, order.getPayStatus(),
                CustomerServiceRepairOrderPayStatusEnum.WAITING_CONFIRM.getStatus(),
                "客户选择对公转账", CustomerServiceOperatorTypeEnum.MEMBER.getType(), memberId);
        return buildOfflineInfo(config);
    }

    private CustomerServiceOfflinePayInfoBO buildOfflineInfo(TradeConfigDO config) {
        CustomerServiceOfflinePayInfoBO info = new CustomerServiceOfflinePayInfoBO();
        info.setAccountName(config.getOfflinePayAccountName());
        info.setAccountNo(config.getOfflinePayAccountNo());
        info.setBankName(config.getOfflinePayBankName());
        info.setTip(config.getOfflinePayTip());
        return info;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CUSTOMER_SERVICE_TYPE, subType = REPAIR_ORDER_CONFIRM_OFFLINE_SUB_TYPE,
            success = REPAIR_ORDER_CONFIRM_OFFLINE_SUCCESS, bizNo = "{{#orderId}}")
    public void confirmOffline(Long orderId, Long adminUserId, String bankSerialNo, String payerName,
                               LocalDateTime paidTime, String remark) {
        CustomerServiceRepairOrderDO order = validateOrder(orderId, null);
        if (!CustomerServiceRepairOrderPayStatusEnum.WAITING_CONFIRM.getStatus().equals(order.getPayStatus())) {
            throw exception(CUSTOMER_SERVICE_OFFLINE_CONFIRM_FAIL);
        }
        // 条件更新是幂等闸门：并发/重复确认只有一次能成功
        int rows = customerServiceRepairOrderMapper.update(null,
                new LambdaUpdateWrapper<CustomerServiceRepairOrderDO>()
                        .eq(CustomerServiceRepairOrderDO::getId, orderId)
                        .eq(CustomerServiceRepairOrderDO::getPayStatus,
                                CustomerServiceRepairOrderPayStatusEnum.WAITING_CONFIRM.getStatus())
                        .set(CustomerServiceRepairOrderDO::getPayStatus,
                                CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus())
                        .set(CustomerServiceRepairOrderDO::getPayChannelCode, "offline")
                        .set(CustomerServiceRepairOrderDO::getPayTime, LocalDateTime.now())
                        .set(CustomerServiceRepairOrderDO::getOfflineConfirmUserId, adminUserId)
                        .set(CustomerServiceRepairOrderDO::getOfflineConfirmTime, LocalDateTime.now()));
        if (rows == 0) {
            throw exception(CUSTOMER_SERVICE_OFFLINE_CONFIRM_FAIL);
        }
        Long payOrderId = payOrderApi.createPaidOfflineOrder(buildPayOrderReq(order)).getCheckedData();
        CustomerServiceRepairOrderDO update = new CustomerServiceRepairOrderDO();
        update.setId(orderId);
        update.setPayOrderId(payOrderId);
        customerServiceRepairOrderMapper.updateById(update);
        StringBuilder detail = new StringBuilder(StrUtil.blankToDefault(remark, "对公转账已确认收款"));
        if (StrUtil.isNotBlank(bankSerialNo)) {
            detail.append("；流水号 ").append(bankSerialNo);
        }
        if (StrUtil.isNotBlank(payerName)) {
            detail.append("；付款人 ").append(payerName);
        }
        if (paidTime != null) {
            detail.append("；到账时间 ").append(paidTime);
        }
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_ORDER.getType(), orderId,
                CustomerServiceActionConstants.PAY_CONFIRM,
                CustomerServiceRepairOrderPayStatusEnum.WAITING_CONFIRM.getStatus(),
                CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus(),
                detail.toString(),
                CustomerServiceOperatorTypeEnum.ADMIN.getType(), adminUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderPaid(Long repairOrderId, Long payOrderId) {
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderMapper.selectById(repairOrderId);
        if (order == null) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_EXISTS);
        }
        if (CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus().equals(order.getPayStatus())) {
            return; // 重复回调：幂等
        }
        if (!CustomerServiceRepairOrderPayStatusEnum.UNPAID.getStatus().equals(order.getPayStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_PAYABLE);
        }
        PayOrderRespDTO payOrder = payOrderApi.getOrder(payOrderId).getCheckedData();
        CustomerServiceRepairOrderDO update = new CustomerServiceRepairOrderDO();
        update.setId(repairOrderId);
        update.setPayStatus(CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus());
        update.setPayOrderId(payOrderId);
        update.setPayChannelCode(payOrder != null ? payOrder.getChannelCode() : null);
        update.setPayTime(LocalDateTime.now());
        customerServiceRepairOrderMapper.updateById(update);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_ORDER.getType(), repairOrderId,
                CustomerServiceActionConstants.PAY_ONLINE, order.getPayStatus(),
                CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus(), "支付成功",
                CustomerServiceOperatorTypeEnum.MEMBER.getType(), order.getMemberId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markShipped(Long repairRequestId, Long memberId, String shipCompany, String shipNo) {
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestService.getRepair(repairRequestId);
        if (memberId != null && !request.getMemberId().equals(memberId)) {
            throw exception(CUSTOMER_SERVICE_REPAIR_NOT_EXISTS);
        }
        if (!CustomerServiceRepairStatusEnum.ORDERED.getStatus().equals(request.getStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_NOT_SHIPPABLE);
        }
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderMapper.selectByRepairRequestId(repairRequestId);
        if (order == null || !CustomerServiceRepairOrderPayStatusEnum.isPaidOrNoPay(order.getPayStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_NOT_PAID);
        }
        if (StrUtil.isBlank(shipNo)) {
            throw exception(CUSTOMER_SERVICE_REPAIR_SHIP_NO_REQUIRED);
        }
        CustomerServiceRepairRequestDO update = new CustomerServiceRepairRequestDO();
        update.setId(repairRequestId);
        update.setStatus(CustomerServiceRepairStatusEnum.SHIPPED.getStatus());
        update.setShipCompany(StrUtil.trimToNull(shipCompany));
        update.setShipNo(shipNo.trim());
        update.setShipTime(LocalDateTime.now());
        customerServiceRepairRequestMapper.updateById(update);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), repairRequestId,
                CustomerServiceActionConstants.SHIP, request.getStatus(),
                CustomerServiceRepairStatusEnum.SHIPPED.getStatus(),
                "客户已寄出：" + StrUtil.blankToDefault(shipCompany, "") + " " + shipNo.trim(),
                CustomerServiceOperatorTypeEnum.MEMBER.getType(), memberId);
    }

    private CustomerServiceRepairOrderDO validateOrder(Long orderId, Long memberId) {
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderMapper.selectById(orderId);
        if (order == null || (memberId != null && !order.getMemberId().equals(memberId))) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_EXISTS);
        }
        return order;
    }

    private PayOrderCreateReqDTO buildPayOrderReq(CustomerServiceRepairOrderDO order) {
        String ip = ServletUtils.getClientIP();
        return new PayOrderCreateReqDTO()
                .setAppKey(tradeOrderProperties.getPayAppKey())
                .setUserIp(StrUtil.blankToDefault(ip, "127.0.0.1"))
                .setUserId(order.getMemberId())
                .setUserType(UserTypeEnum.MEMBER.getValue())
                .setMerchantOrderId(CustomerServiceRepairOrderService.REPAIR_MERCHANT_ORDER_PREFIX + order.getId())
                .setSubject("维修订单 #" + order.getId())
                .setBody("维修订单 #" + order.getId())
                .setPrice(order.getPayableAmount())
                .setExpireTime(LocalDateTime.now().plus(tradeOrderProperties.getPayExpireTime()));
    }

    private void cancelByRequest(CustomerServiceRepairOrderDO order) {
        if (CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus().equals(order.getPayStatus())) {
            // 已退款：保留支付/退款痕迹，只取消维修申请；未退款：先走退款
            if (!CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus().equals(order.getRefundStatus())) {
                throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_PAID_REFUND_FIRST);
            }
            return;
        }
        CustomerServiceRepairOrderDO update = new CustomerServiceRepairOrderDO();
        update.setId(order.getId());
        update.setPayStatus(CustomerServiceRepairOrderPayStatusEnum.CANCELED.getStatus());
        customerServiceRepairOrderMapper.updateById(update);
    }

    private void updateRepairStatus(Long repairRequestId, Integer status) {
        CustomerServiceRepairRequestDO update = new CustomerServiceRepairRequestDO();
        update.setId(repairRequestId);
        update.setStatus(status);
        customerServiceRepairRequestMapper.updateById(update);
    }

    @Override
    public PageResult<CustomerServiceRepairOrderDO> getPage(Long id, Long memberId, Integer payStatus,
                                                            Integer refundStatus, LocalDateTime[] createTime,
                                                            PageParam pageParam) {
        return customerServiceRepairOrderMapper.selectPage(id, memberId, payStatus, refundStatus,
                createTime, pageParam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long orderId, Long adminUserId) {
        CustomerServiceRepairOrderDO order = validateOrder(orderId, null);
        if (order.getCompleteTime() != null) {
            return; // 重复点击：幂等
        }
        if (CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus().equals(order.getRefundStatus())
                || !CustomerServiceRepairOrderPayStatusEnum.isPaidOrNoPay(order.getPayStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_COMPLETABLE);
        }
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestService
                .getRepair(order.getRepairRequestId());
        // 允许 30/40 正常完成；50 用于补齐历史「已完成但未记录完成人」的订单
        if (!CustomerServiceRepairStatusEnum.SHIPPED.getStatus().equals(request.getStatus())
                && !CustomerServiceRepairStatusEnum.REPAIRING.getStatus().equals(request.getStatus())
                && !CustomerServiceRepairStatusEnum.COMPLETED.getStatus().equals(request.getStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_COMPLETABLE);
        }
        CustomerServiceRepairOrderDO update = new CustomerServiceRepairOrderDO();
        update.setId(orderId);
        update.setCompleteTime(LocalDateTime.now());
        update.setCompleteOperatorId(adminUserId);
        customerServiceRepairOrderMapper.updateById(update);
        CustomerServiceRepairRequestDO requestUpdate = new CustomerServiceRepairRequestDO();
        requestUpdate.setId(request.getId());
        requestUpdate.setStatus(CustomerServiceRepairStatusEnum.COMPLETED.getStatus());
        customerServiceRepairRequestMapper.updateById(requestUpdate);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_ORDER.getType(), orderId,
                CustomerServiceActionConstants.COMPLETE, null, null, "完成维修",
                CustomerServiceOperatorTypeEnum.ADMIN.getType(), adminUserId);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), request.getId(),
                CustomerServiceActionConstants.COMPLETE, request.getStatus(),
                CustomerServiceRepairStatusEnum.COMPLETED.getStatus(), "维修完成",
                CustomerServiceOperatorTypeEnum.ADMIN.getType(), adminUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long orderId, Long adminUserId, String userIp, String reason) {
        if (StrUtil.isBlank(reason) || reason.length() > 200) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_REFUND_REASON_REQUIRED);
        }
        CustomerServiceRepairOrderDO order = validateOrder(orderId, null);
        if (!CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus().equals(order.getPayStatus())
                || !CustomerServiceRepairOrderRefundStatusEnum.NOT_REFUNDED.getStatus().equals(order.getRefundStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_NOT_REFUNDABLE);
        }
        CustomerServiceRepairOrderDO update = new CustomerServiceRepairOrderDO();
        update.setId(orderId);
        update.setRefundPrice(order.getPayableAmount());
        update.setRefundReason(reason.trim());
        update.setRefundOperatorId(adminUserId);
        // 在线支付走支付模块原路退；对公/线下确认的由运营人工标记
        boolean offline = "offline".equals(order.getPayChannelCode()) || order.getOfflineConfirmTime() != null;
        if (!offline && order.getPayOrderId() != null) {
            Long payRefundId = payRefundApi.createRefund(new PayRefundCreateReqDTO()
                    .setAppKey(tradeOrderProperties.getPayAppKey())
                    .setUserIp(StrUtil.blankToDefault(userIp, "127.0.0.1"))
                    .setUserId(order.getMemberId()).setUserType(UserTypeEnum.MEMBER.getValue())
                    .setMerchantOrderId(REPAIR_MERCHANT_ORDER_PREFIX + order.getId())
                    .setMerchantRefundId(repairRefundMerchantId(order.getId()))
                    .setReason(reason.trim()).setPrice(order.getPayableAmount())).getCheckedData();
            update.setRefundStatus(CustomerServiceRepairOrderRefundStatusEnum.REFUNDING.getStatus());
            update.setPayRefundId(payRefundId);
        } else {
            update.setRefundStatus(CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus());
            update.setRefundTime(LocalDateTime.now());
        }
        customerServiceRepairOrderMapper.updateById(update);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_ORDER.getType(), orderId,
                CustomerServiceActionConstants.REFUND,
                CustomerServiceRepairOrderRefundStatusEnum.NOT_REFUNDED.getStatus(),
                update.getRefundStatus(),
                StrUtil.format("退款 ¥{}；原因：{}", String.format("%.2f", order.getPayableAmount() / 100.0),
                        reason.trim()),
                CustomerServiceOperatorTypeEnum.ADMIN.getType(), adminUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefunded(String merchantOrderId, String merchantRefundId, Long payRefundId) {
        if (!CustomerServiceRepairOrderService.isRepairMerchantOrderId(merchantOrderId)) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_REFUND_NOTIFY_INVALID);
        }
        Long orderId = CustomerServiceRepairOrderService.parseRepairOrderId(merchantOrderId);
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderMapper.selectById(orderId);
        if (order == null || !repairRefundMerchantId(orderId).equals(merchantRefundId)
                || (order.getPayRefundId() != null && !order.getPayRefundId().equals(payRefundId))) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_REFUND_NOTIFY_INVALID);
        }
        if (CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus().equals(order.getRefundStatus())) {
            return; // 重复回调：幂等
        }
        if (!CustomerServiceRepairOrderRefundStatusEnum.REFUNDING.getStatus().equals(order.getRefundStatus())) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_REFUND_NOTIFY_INVALID);
        }
        PayRefundRespDTO payRefund = payRefundApi.getRefund(payRefundId).getCheckedData();
        if (payRefund == null) {
            throw exception(CUSTOMER_SERVICE_REPAIR_ORDER_REFUND_NOTIFY_INVALID);
        }
        CustomerServiceRepairOrderDO update = new CustomerServiceRepairOrderDO();
        update.setId(orderId);
        if (PayRefundStatusEnum.isSuccess(payRefund.getStatus())) {
            update.setRefundStatus(CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus());
            update.setRefundTime(LocalDateTime.now());
            customerServiceRepairOrderMapper.updateById(update);
            customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_ORDER.getType(), orderId,
                    CustomerServiceActionConstants.REFUND_SUCCESS,
                    CustomerServiceRepairOrderRefundStatusEnum.REFUNDING.getStatus(),
                    CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus(),
                    "原路退款成功", CustomerServiceOperatorTypeEnum.SYSTEM.getType(), null);
        } else if (PayRefundStatusEnum.isFailure(payRefund.getStatus())) {
            update.setRefundStatus(CustomerServiceRepairOrderRefundStatusEnum.REFUND_FAILED.getStatus());
            customerServiceRepairOrderMapper.updateById(update);
            customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_ORDER.getType(), orderId,
                    CustomerServiceActionConstants.REFUND_FAIL,
                    CustomerServiceRepairOrderRefundStatusEnum.REFUNDING.getStatus(),
                    CustomerServiceRepairOrderRefundStatusEnum.REFUND_FAILED.getStatus(),
                    "原路退款失败，请联系支付渠道处理", CustomerServiceOperatorTypeEnum.SYSTEM.getType(), null);
        }
    }

    private String repairRefundMerchantId(Long orderId) {
        return "repair-refund:" + orderId;
    }

}
