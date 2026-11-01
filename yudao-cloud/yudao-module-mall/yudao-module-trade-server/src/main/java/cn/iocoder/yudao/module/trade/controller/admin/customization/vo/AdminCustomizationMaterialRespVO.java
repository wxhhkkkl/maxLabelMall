package cn.iocoder.yudao.module.trade.controller.admin.customization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 定制材质")
@Data
public class AdminCustomizationMaterialRespVO {

    private Long id;
    private String name;
    private String description;
    private Integer basePrice;
    private BigDecimal printCoefficient;
    private BigDecimal printingCoefficient;
    private Integer minQuantity;
    private Integer maxQuantity;
    private Integer minWidth;
    private Integer maxWidth;
    private Integer minHeight;
    private Integer maxHeight;
    private Integer status;
    private Integer sort;
    private List<AreaRule> areaRules;

    @Data
    public static class AreaRule {
        private Long id;
        private BigDecimal minArea;
        private BigDecimal maxArea;
        private BigDecimal printCoefficient;
        private BigDecimal dieCutCoefficient;
    }

}
