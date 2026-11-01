package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationPriceResultBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationQuoteBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationSpecBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;

/**
 * 定制计价 Service（014）
 */
public interface CustomizationPriceService {

    /** 纯计价：不查地址、不含运费 */
    CustomizationPriceResultBO calculate(CustomizationSpecBO spec);

    /** 报价：传了地址时一并算运费与应付 */
    CustomizationQuoteBO quote(CustomizationSpecBO spec, Long userId, Long addressId, Integer deliveryType);

    /** 走既有价格链路生成订单项（供建单使用） */
    TradePriceCalculateRespBO calculateOrderPrice(Long userId, CustomizationSpecBO spec);

}
