package cn.iocoder.yudao.module.trade.controller.app.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 标记维修已寄出 Request VO")
@Data
public class AppCustomerServiceRepairShipReqVO {

    @Schema(description = "维修申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "601")
    @NotNull(message = "维修申请编号不能为空")
    private Long id;

    @Schema(description = "快递公司", example = "顺丰")
    @Size(max = 50, message = "快递公司不能超过 50 字")
    private String shipCompany;

    @Schema(description = "运单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "SF1234567890")
    @NotBlank(message = "请填写运单号")
    @Size(max = 64, message = "运单号不能超过 64 字")
    private String shipNo;

}
