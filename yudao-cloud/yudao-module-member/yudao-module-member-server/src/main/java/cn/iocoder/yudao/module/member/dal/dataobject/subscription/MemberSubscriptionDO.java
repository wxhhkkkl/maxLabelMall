package cn.iocoder.yudao.module.member.dal.dataobject.subscription;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MaxLabel 会员订阅授予流水 DO
 *
 * 一行 = 一次支付授予的一段周期；续费插新行，不改旧行。
 */
@TableName("member_subscription")
@KeySequence("member_subscription_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberSubscriptionDO extends BaseDO {

    @TableId
    private Long id;
    private Long memberId;
    private Long planId;
    /**
     * 发放时的桌面端版本快照：standard 标准版 / professional 专业版 / enterprise 企业版
     */
    private String edition;
    private Long spuId;
    private Long skuId;
    /**
     * 本段授予月数（plan.period_months × 数量）
     */
    private Integer periodMonths;
    /**
     * 本段开始自然日 00:00:00
     */
    private LocalDateTime startTime;
    /**
     * 本段最后有效自然日的次日 00:00:00（不含当日）
     */
    private LocalDateTime endTime;
    private Long sourceOrderId;
    private Long sourceOrderItemId;
    private String sourceOrderNo;
    /**
     * 来源订单或会员订单项是否已全额退款（仅标记，不回收）
     */
    private Boolean refunded;

}
