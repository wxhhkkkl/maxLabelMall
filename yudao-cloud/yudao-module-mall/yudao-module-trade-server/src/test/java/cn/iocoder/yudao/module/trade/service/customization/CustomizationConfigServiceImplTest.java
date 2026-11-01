package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationConfigDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationConfigMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_AREA_RULE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 定制计价参数保存（014）
 */
@Import(CustomizationConfigServiceImpl.class)
public class CustomizationConfigServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CustomizationConfigService configService;
    @Resource
    private CustomizationConfigMapper configMapper;

    @Test
    public void testGetOrDefault_whenEmpty() {
        CustomizationConfigDO config = configService.getConfigOrDefault();
        assertNotNull(config);
        assertEquals(20000, config.getPrintBasePrice());
    }

    @Test
    public void testSave_thenGet() {
        configService.saveConfig(CustomizationConfigDO.builder()
                .printBasePrice(30000).printingBasePrice(70000).dieCutBasePrice(50000)
                .urgentBasePrice(8000).urgentCoefficient(new BigDecimal("1.2")).build());
        assertEquals(30000, configService.getConfig().getPrintBasePrice());
        assertEquals(1, configMapper.selectList().size());
    }

    @Test
    public void testSave_coefficientBelowOneRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> configService.saveConfig(
                CustomizationConfigDO.builder()
                        .printBasePrice(20000).printingBasePrice(60000).dieCutBasePrice(40000)
                        .urgentBasePrice(5000).urgentCoefficient(new BigDecimal("0.9")).build()));
        assertEquals(CUSTOMIZATION_AREA_RULE_INVALID.getCode(), ex.getCode());
    }

}
