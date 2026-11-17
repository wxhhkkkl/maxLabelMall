package cn.iocoder.yudao.module.trade.dal.dataobject.customerservice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 客户服务 - 维修订单 DO（021）
 *
 * 与维修申请 1:0..1，唯一键 `(tenant_id, repair_request_id)` 保证「一申请一单」。
 */
@TableName("trade_repair_order")
@KeySequence("trade_repair_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerServiceRepairOrderDO extends BaseDO {

    @TableId
    private Long id;
    /** 维修申请编号 */
    private Long repairRequestId;
    /** 会员编号 */
    private Long memberId;
    /** 维修费，分 */
    private Integer repairFee;
    /** 寄回运费，分 */
    private Integer returnFreight;
    /** 应付合计，分 */
    private Integer payableAmount;
    /** 费用说明 */
    private String feeRemark;
    /** 支付状态：0 待支付 10 待确认收款 20 已支付 30 无需支付 40 已取消 */
    private Integer payStatus;
    /** 支付渠道 */
    private String payChannelCode;
    /** 支付单号 */
    private Long payOrderId;
    /** 支付成功/确认收款时间 */
    private LocalDateTime payTime;
    /** 对公转账确认人 */
    private Long offlineConfirmUserId;
    /** 对公转账确认时间 */
    private LocalDateTime offlineConfirmTime;
    /** 退款状态：0 未退款 10 退款中 20 已退款 30 退款失败 */
    private Integer refundStatus;
    /** 支付退款单号（在线支付） */
    private Long payRefundId;
    /** 退款金额，分 */
    private Integer refundPrice;
    /** 退款原因 */
    private String refundReason;
    /** 退款完成时间 */
    private LocalDateTime refundTime;
    /** 退款操作人（system_users.id） */
    private Long refundOperatorId;
    /** 完成维修时间 */
    private LocalDateTime completeTime;
    /** 完成维修操作人（system_users.id） */
    private Long completeOperatorId;
    /** 开单管理员 */
    private Long creatorId;

}
