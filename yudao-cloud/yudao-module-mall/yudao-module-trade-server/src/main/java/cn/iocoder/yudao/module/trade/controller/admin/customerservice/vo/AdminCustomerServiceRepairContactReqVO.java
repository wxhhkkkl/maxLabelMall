package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 维修电话联系 Request VO")
@Data
public class AdminCustomerServiceRepairContactReqVO {

    @Schema(description = "维修申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "601")
    @NotNull(message = "维修申请编号不能为空")
    private Long id;

    @Schema(description = "联系结果", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "联系结果不能为空")
    @Size(max = 500, message = "联系结果不能超过 500 字")
    private String contactResult;

}
