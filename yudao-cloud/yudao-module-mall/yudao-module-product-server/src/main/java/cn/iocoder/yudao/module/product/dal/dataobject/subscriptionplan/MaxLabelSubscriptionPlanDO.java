package cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MaxLabel 会员授权商品映射 DO
 *
 * 一条记录 = 一个可售 SKU × 一个周期。
 */
@TableName("maxlabel_subscription_plan")
@KeySequence("maxlabel_subscription_plan_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelSubscriptionPlanDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 商品 SPU 编号
     */
    private Long spuId;
    /**
     * 商品 SKU 编号（购买单元）
     */
    private Long skuId;
    /**
     * 授予周期（自然月）
     */
    private Integer periodMonths;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 排序
     */
    private Integer sort;

}
