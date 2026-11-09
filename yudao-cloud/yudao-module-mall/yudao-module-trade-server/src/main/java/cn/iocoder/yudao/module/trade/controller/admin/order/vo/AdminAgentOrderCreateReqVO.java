package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 代客下单创建 Request VO")
@Data
public class AdminAgentOrderCreateReqVO {

    @Schema(description = "目标客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "目标客户不能为空")
    private Long userId;

    @Schema(description = "商品项数组", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "商品不能为空")
    @Valid
    private List<Item> items;

    @Schema(description = "收件地址编号（实物单必填；虚拟单可空）", example = "1024")
    private Long addressId;

    @Schema(description = "买家留言（≤200）", example = "请在工作日送")
    @Size(max = 200, message = "买家留言最多 200 个字符")
    private String userRemark;

    @Schema(description = "支付渠道：仅支持传 offline（对公转账）；不传 = 在线支付", example = "offline")
    private String payChannelCode;

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
