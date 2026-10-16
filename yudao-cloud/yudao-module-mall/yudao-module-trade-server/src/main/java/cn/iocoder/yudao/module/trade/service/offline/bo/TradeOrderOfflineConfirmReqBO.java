package cn.iocoder.yudao.module.trade.service.offline.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 「确认对公转账收款」的服务层入参（008）
 *
 * ⚠️ 为什么用一个 BO、而不是排 6 个参数：里面有 3 个 String（流水号 / 打款方 / 备注），
 * 位置排错**编译期发现不了**；财务动作的参数更是错不起。
 *
 * 字段的必填口径见 {@code TradeOrderOfflineConfirmReqVO}（流水号必填、其余选填）。
 *
 * @author 芋道源码
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderOfflineConfirmReqBO {

    /**
     * 订单编号
     */
    private Long orderId;

    /**
     * 银行流水号（**必填**，FR-204）—— 非空校验在调用方（订单域）做，
     * 因为它是**"确认收款这个动作"**的约束，而不是本表的约束
     */
    private String bankSerialNo;

    /**
     * 打款方名称（选填）
     */
    private String payerName;

    /**
     * 到账时间（选填）
     */
    private LocalDateTime paidTime;

    /**
     * 备注（选填）
     */
    private String remark;

    /**
     * 确认人（后台操作者），关联 system_users.id —— 财务动作必须可追溯（FR-205）
     */
    private Long confirmUserId;

}
