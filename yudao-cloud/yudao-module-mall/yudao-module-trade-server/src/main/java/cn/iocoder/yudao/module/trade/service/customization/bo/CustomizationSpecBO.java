package cn.iocoder.yudao.module.trade.service.customization.bo;

import lombok.Data;

import java.util.List;

/**
 * 定制规格（014）
 */
@Data
public class CustomizationSpecBO {

    private Long materialId;
    /** 成品宽高，mm */
    private Integer width;
    private Integer height;
    /** 定制数量 */
    private Integer quantity;
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
    private java.math.BigDecimal rowGap;
    private java.math.BigDecimal columnGap;
    /** 轴芯内径 */
    private String axisDiameter;
    /** 实际数量 */
    private Integer labelCount;
    /** LabelShop 模板名称 */
    private String templateName;
    private Boolean urgent;
    /** 设计文件（1~5） */
    private List<CustomizationFileBO> files;
    /** 客户备注 */
    private String remark;

}
