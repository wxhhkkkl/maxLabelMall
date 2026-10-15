package cn.iocoder.yudao.module.trade.controller.admin.invoice.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 开票申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TradeOrderInvoicePageReqVO extends PageParam {

    @Schema(description = "状态：0 待开票，10 已开票，20 已作废", example = "0")
    private Integer status;

    @Schema(description = "订单号（精确匹配 —— 运营手上通常拿着订单号来找）", example = "NO-20261015-0001")
    private String orderNo;

}
