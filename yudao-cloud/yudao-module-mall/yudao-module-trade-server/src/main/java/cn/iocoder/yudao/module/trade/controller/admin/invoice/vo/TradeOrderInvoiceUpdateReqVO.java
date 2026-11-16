package cn.iocoder.yudao.module.trade.controller.admin.invoice.vo;

import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 修改开票信息的 Request VO
 *
 * 只带**可改的字段**：票种 + 抬头类型 + 抬头五件套 + 邮箱。
 * 金额、订单号、状态、发票文件都**不在这里** —— 金额取订单实付、状态由开具流程决定，
 * 不是"改信息"能动的（见 {@code TradeOrderInvoiceServiceImpl#updateInvoice}）。
 *
 * ⚠️ 字段与校验**复用 C 端的 {@link AppInvoiceApplyBaseVO} + {@code InvoiceValidator}**：
 * 抬头必填矩阵随票种与抬头类型而变（企业要税号、收据不要），两处各写一份必然在改票种时漏改一处。
 *
 * @author 芋道源码
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeOrderInvoiceUpdateReqVO extends AppInvoiceApplyBaseVO {

    @Schema(description = "开票申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "77")
    @NotNull(message = "开票申请编号不能为空")
    private Long id;

}
