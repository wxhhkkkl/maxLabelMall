package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationConfigDO;

/**
 * 定制计价参数 Service（014）
 */
public interface CustomizationConfigService {

    /** 当前租户的参数；没有则返回 null */
    CustomizationConfigDO getConfig();

    /** 当前租户的参数；没有则返回默认值（用于报价） */
    CustomizationConfigDO getConfigOrDefault();

    /** 保存（每租户一行） */
    void saveConfig(CustomizationConfigDO config);

}
