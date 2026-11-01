package cn.iocoder.yudao.module.trade.controller.app.customization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户 App - 可选定制材质（只下发给允许范围，不含系数与基价）
 */
@Schema(description = "用户 App - 可选定制材质")
@Data
public class AppCustomizationMaterialRespVO {

    @Schema(description = "材质编号", example = "3")
    private Long id;
    @Schema(description = "材质名称", example = "可移除铜版")
    private String name;
    @Schema(description = "材质说明（展示给客户）", example = "有热敏涂层的平滑亮白纸")
    private String description;

    @Schema(description = "数量下限", example = "1")
    private Integer minQuantity;
    @Schema(description = "数量上限", example = "100000")
    private Integer maxQuantity;
    @Schema(description = "成品宽度下限，mm", example = "10")
    private Integer minWidth;
    @Schema(description = "成品宽度上限，mm", example = "2000")
    private Integer maxWidth;
    @Schema(description = "成品高度下限，mm", example = "10")
    private Integer minHeight;
    @Schema(description = "成品高度上限，mm", example = "2000")
    private Integer maxHeight;

}
