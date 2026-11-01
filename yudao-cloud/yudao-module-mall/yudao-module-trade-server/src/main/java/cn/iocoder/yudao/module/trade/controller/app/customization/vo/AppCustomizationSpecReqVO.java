package cn.iocoder.yudao.module.trade.controller.app.customization.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.trade.enums.customization.CustomizationModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户 App - 定制规格（报价与下单共用）
 */
@Schema(description = "用户 App - 定制规格")
@Data
public class AppCustomizationSpecReqVO {

    @Schema(description = "材质编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "材质不能为空")
    private Long materialId;

    @Schema(description = "成品宽度，mm", requiredMode = Schema.RequiredMode.REQUIRED, example = "50")
    @NotNull(message = "成品宽度不能为空")
    @Min(value = 1, message = "成品宽度必须大于 0")
    private Integer width;

    @Schema(description = "成品高度，mm", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "成品高度不能为空")
    @Min(value = 1, message = "成品高度必须大于 0")
    private Integer height;

    @Schema(description = "定制数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "定制数量不能为空")
    @Min(value = 1, message = "定制数量必须大于 0")
    private Integer quantity;

    @Schema(description = "业务模式：1 标签印刷（多色）/ 2 标签打印（黑色）/ 3 多色印刷+黑色打印",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "业务模式不能为空")
    @InEnum(value = CustomizationModeEnum.class, message = "业务模式不正确")
    private Integer mode;
    /** 成品形态：1 卷筒（卷轴）/ 2 平张（单张）；报价可不传，建单时必填 */
    @Schema(description = "成品形态：1 卷筒（卷轴）/ 2 平张（单张）", example = "2")
    private Integer form;

    @Schema(description = "整张规格：A4 / 其他（平张时使用）", example = "A4")
    private String sheetSpec;

    @Schema(description = "是否模切", example = "false")
    private Boolean dieCut;

    @Schema(description = "边角：1 圆角 / 2 直角", example = "1")
    private Integer finishing;

    @Schema(description = "是否撕裂线", example = "false")
    private Boolean tearLine;

    /** 每张行数；报价可不传，建单时必填 */
    @Schema(description = "每张行数", example = "1")
    @Min(value = 1, message = "每张行数必须大于 0")
    private Integer rowCount;

    /** 每张列数；报价可不传，建单时必填 */
    @Schema(description = "每张列数", example = "1")
    @Min(value = 1, message = "每张列数必须大于 0")
    private Integer columnCount;

    @Schema(description = "行间距，mm", example = "3")
    private java.math.BigDecimal rowGap;

    @Schema(description = "列间距，mm", example = "3")
    private java.math.BigDecimal columnGap;

    @Schema(description = "轴芯内径（卷筒）：25.4mm / 76.2mm / 40mm", example = "25.4mm")
    private String axisDiameter;

    @Schema(description = "实际数量（不传则等于定制数量）", example = "1000")
    private Integer labelCount;

    @Schema(description = "LabelShop 模板名称（选填）", example = "新标签模板1")
    @Size(max = 128, message = "模板名称最多 128 个字符")
    private String templateName;

    @Schema(description = "是否加急", example = "false")
    private Boolean urgent;

    @Schema(description = "设计文件（1~5 个）")
    @Valid
    private List<AppCustomizationFileVO> files;

    @Schema(description = "客户备注")
    @Size(max = 500, message = "备注最多 500 个字符")
    private String remark;

}
