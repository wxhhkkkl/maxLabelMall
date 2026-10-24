package cn.iocoder.yudao.module.trade.controller.admin.delivery.vo.express;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 快递公司精简信息 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryExpressSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "6592")
    @NotNull(message = "编号不能为空")
    private Long id;

    @Schema(description = "快递公司名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "顺丰速运")
    @NotNull(message = "快递公司名称不能为空")
    private String name;

    /**
     * 快递100 的公司编码；为空表示该快递公司暂不支持物流查询
     *
     * 后台订单详情用它**先判断能不能查**（后台拿不到错误码，见 contracts/admin-api.md）。
     */
    @Schema(description = "快递100 编码，为空表示暂不支持查询", example = "shunfeng")
    private String kd100Code;

}
