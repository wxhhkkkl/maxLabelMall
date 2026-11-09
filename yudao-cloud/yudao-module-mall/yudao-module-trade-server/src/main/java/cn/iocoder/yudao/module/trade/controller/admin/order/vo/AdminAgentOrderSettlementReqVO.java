package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 代客下单结算 Request VO")
@Data
public class AdminAgentOrderSettlementReqVO {

    @Schema(description = "目标客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "目标客户不能为空")
    private Long userId;

    @Schema(description = "商品项数组", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "商品不能为空")
    @Valid
    private List<Item> items;

    @Schema(description = "收件地址编号（实物单必填；虚拟单可空）", example = "1024")
    private Long addressId;

    @Data
    @Schema(description = "商品项")
    public static class Item {

        @Schema(description = "商品 SKU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
        @NotNull(message = "商品 SKU 编号不能为空")
        private Long skuId;

        @Schema(description = "购买数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量最小值为 1")
        private Integer count;

    }

}
