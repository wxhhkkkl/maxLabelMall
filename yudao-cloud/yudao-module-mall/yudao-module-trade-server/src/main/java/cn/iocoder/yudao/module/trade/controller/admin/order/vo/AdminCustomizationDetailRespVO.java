package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台 - 定制订单要求（014）
 *
 * 与 C 端相比多一个 `paramSnapshot`：后台要能看到**下单当时**的材质 / 区间 / 初始价，
 * 这是客诉时解释金额的依据（SC-142 / FR-376）。
 */
@Schema(description = "管理后台 - 定制订单要求")
@Data
public class AdminCustomizationDetailRespVO {

    @Schema(description = "定制扩展编号")
    private Long id;
    @Schema(description = "材质编号")
    private Long materialId;
    @Schema(description = "材质名称")
    private String materialName;
    @Schema(description = "成品宽度，mm")
    private Integer width;
    @Schema(description = "成品高度，mm")
    private Integer height;
    @Schema(description = "定制数量")
    private Integer quantity;
    @Schema(description = "单件面积，㎡")
    private BigDecimal unitArea;
    @Schema(description = "整单总面积，㎡")
    private BigDecimal totalArea;
    @Schema(description = "业务模式：1 印刷（多色）/ 2 打印（黑色）/ 3 两者 / 4 空白标签")
    private Integer mode;
    @Schema(description = "成品形态：1 卷筒 / 2 平张")
    private Integer form;
    @Schema(description = "整张规格")
    private String sheetSpec;
    @Schema(description = "是否模切")
    private Boolean dieCut;
    @Schema(description = "边角：1 圆角 / 2 直角")
    private Integer finishing;
    @Schema(description = "是否撕裂线")
    private Boolean tearLine;
    @Schema(description = "每张行数 / 列数")
    private Integer rowCount;
    private Integer columnCount;
    @Schema(description = "行间距 / 列间距，mm")
    private BigDecimal rowGap;
    private BigDecimal columnGap;
    @Schema(description = "轴芯内径")
    private String axisDiameter;
    @Schema(description = "实际数量")
    private Integer labelCount;
    @Schema(description = "模板名称")
    private String templateName;
    @Schema(description = "是否加急")
    private Boolean urgent;
    @Schema(description = "客户备注")
    private String remark;
    @Schema(description = "价格明细（分）")
    private Breakdown breakdown;
    @Schema(description = "设计文件")
    private List<File> files;
    @Schema(description = "下单时的参数快照")
    private Object paramSnapshot;

    @Data
    public static class Breakdown {
        private Integer print;
        private Integer printing;
        private Integer dieCut;
        private Integer urgent;
        private Integer subtotal;
        private Integer total;
    }

    @Data
    public static class File {
        private String name;
        private String url;
        private Long size;
        private String type;
    }

}
