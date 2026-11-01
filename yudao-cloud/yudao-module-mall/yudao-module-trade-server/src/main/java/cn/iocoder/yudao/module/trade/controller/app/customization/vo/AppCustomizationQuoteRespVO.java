package cn.iocoder.yudao.module.trade.controller.app.customization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户 App - 定制报价响应（金额单位：分；面积单位：㎡）
 */
@Schema(description = "用户 App - 定制报价")
@Data
public class AppCustomizationQuoteRespVO {

    @Schema(description = "材质编号", example = "3")
    private Long materialId;
    @Schema(description = "材质名称", example = "可移除铜版")
    private String materialName;

    @Schema(description = "单件面积，㎡", example = "0.0050")
    private BigDecimal unitArea;
    @Schema(description = "整单总面积，㎡", example = "5.0000")
    private BigDecimal totalArea;

    @Schema(description = "价格明细")
    private Breakdown breakdown;

    @Schema(description = "运费，分", example = "800")
    private Integer freight;
    @Schema(description = "应付金额，分", example = "34900")
    private Integer payable;

    @Schema(description = "报价时间")
    private LocalDateTime quoteTime;

    @Data
    public static class Breakdown {
        @Schema(description = "打印，分")
        private Integer print;
        @Schema(description = "印刷，分")
        private Integer printing;
        @Schema(description = "模切，分")
        private Integer dieCut;
        @Schema(description = "加急，分")
        private Integer urgent;
        @Schema(description = "小计，分")
        private Integer subtotal;
        @Schema(description = "合计，分")
        private Integer total;
    }

}
