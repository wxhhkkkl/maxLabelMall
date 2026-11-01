package cn.iocoder.yudao.module.trade.dal.dataobject.customization;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 定制印刷计价参数 DO（014，每租户一行）
 */
@TableName("trade_customization_config")
@KeySequence("trade_customization_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomizationConfigDO extends BaseDO {

    @TableId
    private Long id;
    /** 打印初始价，分 */
    private Integer printBasePrice;
    /** 印刷初始价，分 */
    private Integer printingBasePrice;
    /** 模切初始价，分 */
    private Integer dieCutBasePrice;
    /** 加急初始价，分 */
    private Integer urgentBasePrice;
    /** 加急系数 */
    private BigDecimal urgentCoefficient;

}
