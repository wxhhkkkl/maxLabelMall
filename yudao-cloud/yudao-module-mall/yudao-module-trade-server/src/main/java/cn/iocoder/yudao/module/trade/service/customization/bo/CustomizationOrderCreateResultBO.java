package cn.iocoder.yudao.module.trade.service.customization.bo;

import lombok.Data;

/**
 * 创建定制订单的结果（014）
 *
 * changed = true 时表示「价格已变」，quote 是新报价，订单未创建（Q101）。
 */
@Data
public class CustomizationOrderCreateResultBO {

    private boolean changed;
    /** 价格已变时的新报价 */
    private CustomizationQuoteBO quote;

    /** 创建成功时返回 */
    private Long orderId;
    private Long payOrderId;
    private Long customizationOrderId;
    /** 重复提交命中时：已有订单 */
    private boolean duplicated;

}
