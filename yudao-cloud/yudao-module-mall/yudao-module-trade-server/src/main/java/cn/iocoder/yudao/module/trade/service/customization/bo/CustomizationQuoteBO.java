package cn.iocoder.yudao.module.trade.service.customization.bo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 定制报价（014）
 *
 * 金额单位为分；面积为 ㎡。
 */
@Data
public class CustomizationQuoteBO {

    private Long materialId;
    private String materialName;
    /** 单件面积 / 整单总面积，㎡ */
    private BigDecimal unitArea;
    private BigDecimal totalArea;
    /** 价格明细 */
    private Breakdown breakdown;
    /** 运费（未传地址时为 0） */
    private Integer freight;
    /** 应付 = 合计 + 运费 */
    private Integer payable;
    private LocalDateTime quoteTime;
    /** 下单时的参数快照 JSON（报价时为 null） */
    private String paramSnapshot;

    @Data
    public static class Breakdown {
        private Integer print;
        private Integer printing;
        private Integer dieCut;
        private Integer urgent;
        private Integer subtotal;
        private Integer total;
    }

}
