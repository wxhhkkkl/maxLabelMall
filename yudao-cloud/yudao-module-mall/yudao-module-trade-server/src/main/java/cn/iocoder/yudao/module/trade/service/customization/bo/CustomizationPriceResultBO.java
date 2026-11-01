package cn.iocoder.yudao.module.trade.service.customization.bo;

import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationAreaRuleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationMaterialDO;
import lombok.Data;

/**
 * 计价中间结果（014）：报价、建单、快照共用
 */
@Data
public class CustomizationPriceResultBO {

    private CustomizationMaterialDO material;
    private CustomizationAreaRuleDO areaRule;
    private CustomizationConfigDO config;
    private CustomizationQuoteBO quote;

}
