package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceApplyBaseVO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

@Schema(description = "用户 App - 交易订单创建 Request VO")
@Data
public class AppTradeOrderCreateReqVO extends AppTradeOrderSettlementReqVO {

    @Schema(description = "备注", example = "这个是我的订单哟")
    private String remark;

    /**
     * 开票信息（006）
     *
     * ⚠️ **不传 = 不开票**，此时不产生任何开票记录（FR-159）—— 前端必须用 `undefined`
     * 而不是 `null` / 空对象，后者语义含混（"申请了但字段全空"）。
     *
     * ⚠️ 它与订单**在同一事务**里落库（Q32）：要么都成、要么都不成。
     * 校验失败（缺项 / 税号非法）会**拒绝整单** —— 那是客户能改的输入错误；
     * 而"实付 0 元"是**忽略**（订单照下），见 `TradeOrderInvoiceServiceImpl#createInvoice`。
     */
    private AppInvoiceApplyBaseVO invoice;

    @AssertTrue(message = "配送方式不能为空")
    @JsonIgnore
    public boolean isDeliveryTypeNotNull() {
        return getDeliveryType() != null;
    }

}
