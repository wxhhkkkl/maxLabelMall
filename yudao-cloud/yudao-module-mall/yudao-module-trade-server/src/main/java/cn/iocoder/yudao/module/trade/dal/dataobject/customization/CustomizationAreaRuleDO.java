package cn.iocoder.yudao.module.trade.dal.dataobject.customization;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 定制印刷面积区间规则 DO（014）
 */
@TableName("trade_customization_area_rule")
@KeySequence("trade_customization_area_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomizationAreaRuleDO extends BaseDO {

    @TableId
    private Long id;
    /** 材质编号 */
    private Long materialId;
    /** 区间下界（含），㎡ */
    private BigDecimal minArea;
    /** 区间上界（不含），㎡；0 表示无上限 */
    private BigDecimal maxArea;
    /** 区间打印系数 */
    private BigDecimal printCoefficient;
    /** 模切系数；0 表示不收按面积费、仍收初始价（Q105） */
    private BigDecimal dieCutCoefficient;
    /** 排序 */
    private Integer sort;

}
