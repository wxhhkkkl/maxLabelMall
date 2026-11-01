package cn.iocoder.yudao.module.trade.dal.dataobject.customization;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 定制印刷订单扩展 DO（014，与 trade_order 1:1）
 */
@TableName("trade_customization_order")
@KeySequence("trade_customization_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomizationOrderDO extends BaseDO {

    @TableId
    private Long id;
    /** 交易订单编号 */
    private Long orderId;
    /** 材质编号 */
    private Long materialId;
    /** 材质名称快照 */
    private String materialName;
    /** 成品宽高，mm */
    private Integer width;
    private Integer height;
    /** 定制数量 */
    private Integer quantity;
    /** 单件面积 / 整单总面积，㎡ */
    private BigDecimal unitArea;
    private BigDecimal totalArea;
    /** 业务模式：1 印刷（多色）/ 2 打印（黑色）/ 3 两者 / 4 空白标签（Q108） */
    private Integer mode;
    /** 成品形态：1 卷筒（卷轴）/ 2 平张（单张） */
    private Integer form;
    /** 整张规格：A4 / 其他 */
    private String sheetSpec;
    /** 是否模切 */
    private Boolean dieCut;
    /** 边角：1 圆角 / 2 直角 */
    private Integer finishing;
    /** 是否撕裂线 */
    private Boolean tearLine;
    /** 每张行数 / 列数 */
    private Integer rowCount;
    private Integer columnCount;
    /** 行间距 / 列间距，mm */
    private BigDecimal rowGap;
    private BigDecimal columnGap;
    /** 轴芯内径（卷筒）：25.4mm / 76.2mm / 40mm */
    private String axisDiameter;
    /** 实际数量 */
    private Integer labelCount;
    /** LabelShop 模板名称（选填） */
    private String templateName;
    private Boolean urgent;
    /** 客户备注 */
    private String remark;
    /** 价格明细 JSON（分） */
    private String priceBreakdown;
    /** 下单时参数快照 JSON */
    private String paramSnapshot;
    /** 设计文件 JSON 数组 */
    private String files;

}
