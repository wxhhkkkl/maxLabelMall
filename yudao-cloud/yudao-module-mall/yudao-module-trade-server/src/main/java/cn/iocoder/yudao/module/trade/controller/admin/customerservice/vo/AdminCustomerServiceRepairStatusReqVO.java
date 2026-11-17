package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 推进维修状态 Request VO")
@Data
public class AdminCustomerServiceRepairStatusReqVO {

    @Schema(description = "维修申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "601")
    @NotNull(message = "维修申请编号不能为空")
    private Long id;

    @Schema(description = "目标状态：40 维修中 / 50 已完成", requiredMode = Schema.RequiredMode.REQUIRED, example = "40")
    @NotNull(message = "目标状态不能为空")
    private Integer status;

    @Schema(description = "变更说明", example = "已收到设备，开始维修")
    private String remark;

}
