package cn.iocoder.yudao.module.trade.controller.admin.customization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 面积区间规则")
@Data
public class AdminCustomizationAreaRuleSaveReqVO {

    @Schema(description = "区间下界（含），㎡", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "区间下界不能为空")
    private BigDecimal minArea;

    @Schema(description = "区间上界（不含），㎡；0 表示无上限", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "区间上界不能为空")
    private BigDecimal maxArea;

    @Schema(description = "区间打印系数", requiredMode = Schema.RequiredMode.REQUIRED, example = "2.0")
    @NotNull(message = "区间打印系数不能为空")
    private BigDecimal printCoefficient;

    @Schema(description = "模切系数；0 表示不收按面积费、仍收初始价", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.0")
    @NotNull(message = "模切系数不能为空")
    private BigDecimal dieCutCoefficient;

}
