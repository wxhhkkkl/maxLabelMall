package cn.iocoder.yudao.module.trade.dal.dataobject.customization;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 定制印刷材质 DO（014）
 */
@TableName("trade_customization_material")
@KeySequence("trade_customization_material_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomizationMaterialDO extends BaseDO {

    @TableId
    private Long id;
    /** 材质名称 */
    private String name;
    /** 材质说明（展示给客户） */
    private String description;
    /** 材质基价，单位：分/㎡ */
    private Integer basePrice;
    /** 材质打印系数 */
    private BigDecimal printCoefficient;
    /** 印刷系数 */
    private BigDecimal printingCoefficient;
    /** 数量上下限 */
    private Integer minQuantity;
    private Integer maxQuantity;
    /** 成品宽高上下限，mm */
    private Integer minWidth;
    private Integer maxWidth;
    private Integer minHeight;
    private Integer maxHeight;
    /** 状态：0 启用 1 停用 */
    private Integer status;
    /** 排序 */
    private Integer sort;

}
