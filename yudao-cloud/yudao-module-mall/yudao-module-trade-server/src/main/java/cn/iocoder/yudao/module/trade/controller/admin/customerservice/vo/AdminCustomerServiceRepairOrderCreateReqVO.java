package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 生成维修订单 Request VO")
@Data
public class AdminCustomerServiceRepairOrderCreateReqVO {

    @Schema(description = "维修申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "601")
    @NotNull(message = "维修申请编号不能为空")
    private Long repairRequestId;

    @Schema(description = "维修费，分", requiredMode = Schema.RequiredMode.REQUIRED, example = "10000")
    @NotNull(message = "维修费不能为空")
    @Min(value = 0, message = "维修费不能为负数")
    private Integer repairFee;

    @Schema(description = "寄回运费，分", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")
    @NotNull(message = "寄回运费不能为空")
    @Min(value = 0, message = "寄回运费不能为负数")
    private Integer returnFreight;

    @Schema(description = "费用说明", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "费用说明不能为空")
    @Size(max = 500, message = "费用说明不能超过 500 字")
    private String feeRemark;

}
