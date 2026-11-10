package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 更新订单 ERP 开单状态 Request VO（2026-11-10）
 *
 * <p>ERP 开单是**独立于订单主状态**的业务标记：现在由运营手工维护，未来由 ERP 对接回写。
 */
@Schema(description = "管理后台 - 更新订单 ERP 开单状态 Request VO")
@Data
public class TradeOrderErpReqVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "订单编号不能为空")
    private Long id;

    @Schema(description = "是否已开单：true 标记已开单；false 撤销开单", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "ERP开单状态不能为空")
    private Boolean erpCreated;

    @Schema(description = "ERP系统单号（标记已开单时必填）", example = "ERP-20261110-001")
    @Size(max = 64, message = "ERP单号长度不能超过 64 个字符")
    private String erpNo;

    @AssertTrue(message = "标记ERP已开单时，ERP单号不能为空")
    public boolean isErpNoValid() {
        // 撤销开单不要求单号；标记已开单时必须填写
        return !Boolean.TRUE.equals(erpCreated) || StrUtil.isNotBlank(erpNo);
    }

}
