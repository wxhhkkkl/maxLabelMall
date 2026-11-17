package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 投诉咨询处理 Request VO")
@Data
public class AdminCustomerServiceTicketHandleReqVO {

    @Schema(description = "工单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "501")
    @NotNull(message = "工单编号不能为空")
    private Long id;

    @Schema(description = "处理说明", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "处理说明不能为空")
    @Size(max = 500, message = "处理说明不能超过 500 字")
    private String handleRemark;

}
