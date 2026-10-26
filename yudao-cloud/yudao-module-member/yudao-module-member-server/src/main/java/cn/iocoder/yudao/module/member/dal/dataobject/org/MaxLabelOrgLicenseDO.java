package cn.iocoder.yudao.module.member.dal.dataobject.org;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MaxLabel 企业授权批次 DO
 *
 * 一行 = 一次支付授予的一段周期 × 一批席位。再次购买时新增一行，并把旧批次打成
 * {@link #foldedTime}（折算）：这里没有「续费铺时长」与「扩席位」两种语义 ——
 * 购买数量一律是该次购买后的**总席位数**，旧批次的剩余时长被折算进新批次（Q78）。
 * end_time 为最后有效自然日的次日 00:00（不含当日）。
 */
@TableName("maxlabel_org_license")
@KeySequence("maxlabel_org_license_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelOrgLicenseDO extends BaseDO {

    @TableId
    private Long id;
    private Long orgId;
    private Long planId;
    private Long spuId;
    private Long skuId;
    /** 本段**购买的**月数（plan.periodMonths；折算进来的剩余时长不并入这里，以起止日期为准） */
    private Integer periodMonths;
    /** 本批授予席位数（= 订单项数量） */
    private Integer seatsTotal;
    /** 本段开始自然日 00:00:00 */
    private LocalDateTime startTime;
    /** 本段最后有效自然日的次日 00:00:00（不含当日） */
    private LocalDateTime endTime;
    private Long sourceOrderId;
    private Long sourceOrderItemId;
    private String sourceOrderNo;
    /** 来源订单或订单项是否已全额退款（仅标记，不回收） */
    private Boolean refunded;
    /**
     * 折算时间：非空 = 本批次的剩余时长已经并入后续批次，只留痕，
     * **不再参与席位容量、企业到期日与下次续费起点**（Q78）。
     */
    private LocalDateTime foldedTime;

}
