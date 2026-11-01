package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationConfigDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationConfigMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_AREA_RULE_INVALID;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationLogRecordConstants.*;

@Service
@Validated
public class CustomizationConfigServiceImpl implements CustomizationConfigService {

    /** 没有配置行时的默认值（与截图一致：200 / 600 / 400 / 50 元，1.1） */
    public static CustomizationConfigDO defaultConfig() {
        return CustomizationConfigDO.builder()
                .printBasePrice(20000).printingBasePrice(60000)
                .dieCutBasePrice(40000).urgentBasePrice(5000)
                .urgentCoefficient(new BigDecimal("1.1000"))
                .build();
    }

    @Resource
    private CustomizationConfigMapper customizationConfigMapper;

    @Override
    public CustomizationConfigDO getConfig() {
        return customizationConfigMapper.selectOne();
    }

    @Override
    public CustomizationConfigDO getConfigOrDefault() {
        CustomizationConfigDO config = getConfig();
        return config != null ? config : defaultConfig();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CUSTOMIZATION_TYPE, subType = CONFIG_SAVE_SUB_TYPE,
            bizNo = "customization-config", success = CONFIG_SAVE_SUCCESS)
    public void saveConfig(CustomizationConfigDO config) {
        validate(config);
        CustomizationConfigDO exists = getConfig();
        if (exists == null) {
            config.setId(null);
            customizationConfigMapper.insert(config);
        } else {
            config.setId(exists.getId());
            customizationConfigMapper.updateById(config);
        }
    }

    private void validate(CustomizationConfigDO config) {
        if (config.getPrintBasePrice() == null || config.getPrintBasePrice() < 0
                || config.getPrintingBasePrice() == null || config.getPrintingBasePrice() < 0
                || config.getDieCutBasePrice() == null || config.getDieCutBasePrice() < 0
                || config.getUrgentBasePrice() == null || config.getUrgentBasePrice() < 0) {
            throw exception(CUSTOMIZATION_AREA_RULE_INVALID);
        }
        if (config.getUrgentCoefficient() == null
                || config.getUrgentCoefficient().compareTo(BigDecimal.ONE) < 0) {
            throw exception(CUSTOMIZATION_AREA_RULE_INVALID);
        }
    }

}
