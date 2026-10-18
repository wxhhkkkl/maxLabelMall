package cn.iocoder.yudao.module.trade.service.offline;

import cn.iocoder.yudao.module.trade.dal.dataobject.offline.TradeOrderOfflinePaymentDO;
import cn.iocoder.yudao.module.trade.dal.mysql.offline.TradeOrderOfflinePaymentMapper;
import cn.iocoder.yudao.module.trade.service.offline.bo.TradeOrderOfflineConfirmReqBO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 对公转账信息的 Service 实现类（008）
 *
 * @author 芋道源码
 */
@Service
@Validated
public class TradeOrderOfflinePaymentServiceImpl implements TradeOrderOfflinePaymentService {

    @Resource
    private TradeOrderOfflinePaymentMapper offlinePaymentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOfflinePayment(Long orderId) {
        // 幂等：同一个订单重复调用不插第二条（`uk_order_id` 也会兜底，但不该靠数据库报错来实现幂等）
        if (offlinePaymentMapper.existsByOrderId(orderId)) {
            return;
        }
        offlinePaymentMapper.insert(TradeOrderOfflinePaymentDO.builder()
                .orderId(orderId)
                .build()); // ⚠️ 只记"这一单走对公转账"；流水号等要等财务确认收款时才填
    }

    @Override
    public TradeOrderOfflinePaymentDO getOfflinePaymentByOrderId(Long orderId) {
        return offlinePaymentMapper.selectByOrderId(orderId);
    }

    @Override
    public List<TradeOrderOfflinePaymentDO> getOfflinePaymentListByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        return offlinePaymentMapper.selectListByOrderIds(orderIds);
    }

    @Override
    public boolean isWaitingReceipt(Long orderId) {
        TradeOrderOfflinePaymentDO offlinePayment = offlinePaymentMapper.selectByOrderId(orderId);
        // 「待收款」= **是对公单 且 还没确认** —— ⚠️ 这里**不判订单状态**：
        // 订单是否还是"待支付"由调用方结合订单一起判（本服务只看自己这张表）
        return offlinePayment != null && offlinePayment.getConfirmTime() == null;
    }

    @Override
    public Set<Long> getWaitingReceiptOrderIds(Collection<Long> orderIds) {
        Set<Long> orderIdSet = new HashSet<>();
        for (TradeOrderOfflinePaymentDO offlinePayment : getOfflinePaymentListByOrderIds(orderIds)) {
            if (offlinePayment.getConfirmTime() == null) { // 与 isWaitingReceipt 同一口径
                orderIdSet.add(offlinePayment.getOrderId());
            }
        }
        return orderIdSet;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmReceipt(TradeOrderOfflineConfirmReqBO reqBO) {
        // ⚠️ **条件更新**：`confirm_time IS NULL` 是幂等闸门 —— 重复 / 并发提交只有一次能改到行。
        //    读一次只是为了拿到主键（本表按 order_id 唯一），真正的并发保护在 WHERE 里。
        TradeOrderOfflinePaymentDO offlinePayment = offlinePaymentMapper.selectByOrderId(reqBO.getOrderId());
        if (offlinePayment == null) {
            return false; // 不是对公单
        }
        int updateCount = offlinePaymentMapper.update(
                TradeOrderOfflinePaymentDO.builder()
                        .bankSerialNo(reqBO.getBankSerialNo()).payerName(reqBO.getPayerName())
                        .paidTime(reqBO.getPaidTime()).remark(reqBO.getRemark())
                        .confirmUserId(reqBO.getConfirmUserId()).confirmTime(LocalDateTime.now())
                        .build(),
                new LambdaUpdateWrapper<TradeOrderOfflinePaymentDO>()
                        .eq(TradeOrderOfflinePaymentDO::getId, offlinePayment.getId())
                        .isNull(TradeOrderOfflinePaymentDO::getConfirmTime));
        return updateCount > 0;
    }

}
