package cn.iocoder.yudao.module.trade.controller.admin.delivery.vo.express;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
* 快递公司 Base VO，提供给添加、修改、详细的子 VO 使用
* 如果子 VO 存在差异的字段，请不要添加到这里，影响 Swagger 文档生成
*/
@Data
public class DeliveryExpressBaseVO {

    @Schema(description = "快递公司编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "快递公司编码不能为空")
    private String code;

    /**
     * 快递100 的公司编码（实时查询的 com 参数）
     *
     * ⚠️ 与上面的 {@link #code} 不是一回事：`code` 是微信小程序物流同步用的短码，
     * 这一列才是快递100 要的全拼。选填；为空表示暂不支持查询。
     */
    @Schema(description = "快递100 编码（com 参数），为空表示暂不支持查询", example = "shunfeng")
    @Size(max = 64, message = "快递100 编码长度不能超过 64")
    private String kd100Code;

    @Schema(description = "快递公司名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    @NotNull(message = "快递公司名称不能为空")
    private String name;

    @Schema(description = "快递公司logo")
    private String logo;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "排序不能为空")
    private Integer sort;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "状态不能为空")
    private Integer status;

}
