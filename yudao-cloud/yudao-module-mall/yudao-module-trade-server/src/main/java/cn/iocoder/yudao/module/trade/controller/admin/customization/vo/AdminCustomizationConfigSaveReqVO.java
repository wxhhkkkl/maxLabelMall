package cn.iocoder.yudao.module.trade.controller.admin.customization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 定制计价参数")
@Data
public class AdminCustomizationConfigSaveReqVO {

    @Schema(description = "打印初始价，分", requiredMode = Schema.RequiredMode.REQUIRED, example = "20000")
    @NotNull(message = "打印初始价不能为空")
    @Min(value = 0, message = "打印初始价不能为负")
    private Integer printBasePrice;

    @Schema(description = "印刷初始价，分", requiredMode = Schema.RequiredMode.REQUIRED, example = "60000")
    @NotNull(message = "印刷初始价不能为空")
    @Min(value = 0, message = "印刷初始价不能为负")
    private Integer printingBasePrice;

    @Schema(description = "模切初始价，分", requiredMode = Schema.RequiredMode.REQUIRED, example = "40000")
    @NotNull(message = "模切初始价不能为空")
    @Min(value = 0, message = "模切初始价不能为负")
    private Integer dieCutBasePrice;

    @Schema(description = "加急初始价，分", requiredMode = Schema.RequiredMode.REQUIRED, example = "5000")
    @NotNull(message = "加急初始价不能为空")
    @Min(value = 0, message = "加急初始价不能为负")
    private Integer urgentBasePrice;

    @Schema(description = "加急系数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.1")
    @NotNull(message = "加急系数不能为空")
    private BigDecimal urgentCoefficient;

}
