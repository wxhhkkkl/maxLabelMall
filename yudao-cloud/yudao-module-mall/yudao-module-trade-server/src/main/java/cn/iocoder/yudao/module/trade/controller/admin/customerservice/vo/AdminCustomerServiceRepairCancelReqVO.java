package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 取消维修申请 Request VO")
@Data
public class AdminCustomerServiceRepairCancelReqVO {

    @Schema(description = "维修申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "601")
    @NotNull(message = "维修申请编号不能为空")
    private Long id;

    @Schema(description = "取消原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "取消原因不能为空")
    @Size(max = 255, message = "取消原因不能超过 255 字")
    private String reason;

}
