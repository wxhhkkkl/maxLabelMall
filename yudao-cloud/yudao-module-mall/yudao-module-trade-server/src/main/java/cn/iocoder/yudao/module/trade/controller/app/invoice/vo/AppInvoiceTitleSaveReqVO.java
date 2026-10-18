package cn.iocoder.yudao.module.trade.controller.app.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 发票抬头簿的新增 / 修改 Request VO
 *
 * 字段（含**票种**）直接继承 `AppInvoiceApplyBaseVO` —— Q36 之后抬头与申请**共用同一套字段与校验**
 * （都按票种判必填），各写一份必然分叉。
 *
 * @author 芋道源码
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AppInvoiceTitleSaveReqVO extends AppInvoiceApplyBaseVO {

    @Schema(description = "抬头编号（新增时为空）", example = "3")
    private Long id;

    @Schema(description = "是否设为默认抬头", example = "true")
    private Boolean defaultStatus;

}
