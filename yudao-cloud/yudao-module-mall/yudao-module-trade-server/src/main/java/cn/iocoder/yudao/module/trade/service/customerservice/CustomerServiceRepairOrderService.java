package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairOrderDO;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceOfflinePayInfoBO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 客户服务 - 维修订单 Service（021 / US72 + US73）
 *
 * 跨聚合编排统一放在本 service：依赖维修申请 service，反向不依赖。
 */
public interface CustomerServiceRepairOrderService {

    /** 维修支付单的 merchantOrderId 前缀；纯数字是交易订单号，不会冲突 */
    String REPAIR_MERCHANT_ORDER_PREFIX = "repair:";

    static boolean isRepairMerchantOrderId(String merchantOrderId) {
        return merchantOrderId != null && merchantOrderId.startsWith(REPAIR_MERCHANT_ORDER_PREFIX);
    }

    static Long parseRepairOrderId(String merchantOrderId) {
        return Long.valueOf(merchantOrderId.substring(REPAIR_MERCHANT_ORDER_PREFIX.length()));
    }

    /** 电话联系后开单；一申请一单，0 元自动无需支付 */
    Long createOrder(Long repairRequestId, Integer repairFee, Integer returnFreight,
                     String feeRemark, Long adminUserId);

    /** 记录电话联系结果，申请状态 0/10 → 10 */
    void recordContact(Long repairRequestId, String contactResult, Long adminUserId);

    /** 取消维修申请：未支付订单置已取消；已支付提示先人工退款 */
    void cancel(Long repairRequestId, String reason, Long adminUserId);

    CustomerServiceRepairOrderDO getByRepairRequestId(Long repairRequestId);

    CustomerServiceRepairOrderDO getById(Long id);

    List<CustomerServiceRepairOrderDO> getListByRepairRequestIds(Collection<Long> repairRequestIds);

    /** 客户发起在线支付；首次创建支付单，重复调用复用 */
    Long payOnline(Long orderId, Long memberId);

    /** 客户选择对公转账；返回收款信息 */
    CustomerServiceOfflinePayInfoBO chooseOffline(Long orderId, Long memberId);

    /** 管理端确认对公收款 */
    void confirmOffline(Long orderId, Long adminUserId, String bankSerialNo, String payerName,
                        LocalDateTime paidTime, String remark);

    /** 支付回调：把维修订单置为已支付（幂等） */
    void updateOrderPaid(Long repairOrderId, Long payOrderId);

    /** 客户标记已寄出（20→30） */
    void markShipped(Long repairRequestId, Long memberId, String shipCompany, String shipNo);

    /** 管理端维修订单分页 */
    PageResult<CustomerServiceRepairOrderDO> getPage(Long id, Long memberId, Integer payStatus,
                                                     Integer refundStatus, LocalDateTime[] createTime,
                                                     PageParam pageParam);

    /** 管理端手动完成维修：记录完成人与时间，维修申请置 50 已完成 */
    void complete(Long orderId, Long adminUserId);

    /** 管理端全额退款：在线支付原路退；线下确认的由运营标记已退款 */
    void refund(Long orderId, Long adminUserId, String userIp, String reason);

    /** 支付退款回调：退款中 → 已退款 / 退款失败 */
    void updateRefunded(String merchantOrderId, String merchantRefundId, Long payRefundId);

}
