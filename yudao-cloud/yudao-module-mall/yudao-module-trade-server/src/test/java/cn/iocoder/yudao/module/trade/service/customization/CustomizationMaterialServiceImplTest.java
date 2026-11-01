package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationAreaRuleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationMaterialDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationAreaRuleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationMaterialMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_AREA_RULE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 定制材质保存校验（014 / Q100=A）
 */
@Import(CustomizationMaterialServiceImpl.class)
public class CustomizationMaterialServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CustomizationMaterialService materialService;
    @Resource
    private CustomizationMaterialMapper materialMapper;
    @Resource
    private CustomizationAreaRuleMapper areaRuleMapper;

    private CustomizationMaterialDO material() {
        return CustomizationMaterialDO.builder()
                .name("可移除铜版").basePrice(1138)
                .printCoefficient(new BigDecimal("4")).printingCoefficient(new BigDecimal("1.5"))
                .minQuantity(1).maxQuantity(100000)
                .minWidth(10).maxWidth(2000).minHeight(10).maxHeight(2000)
                .status(0).sort(0).build();
    }

    private CustomizationAreaRuleDO rule(String min, String max, String print, String dieCut) {
        return CustomizationAreaRuleDO.builder()
                .minArea(new BigDecimal(min)).maxArea(new BigDecimal(max))
                .printCoefficient(new BigDecimal(print)).dieCutCoefficient(new BigDecimal(dieCut))
                .build();
    }

    @Test
    public void testCreate_validRules() {
        Long id = materialService.createMaterial(material(), List.of(
                rule("0", "10", "2.0", "1.0"),
                rule("10", "0", "1.4", "0")));
        assertEquals(2, areaRuleMapper.selectListByMaterialId(id).size());
        assertEquals(1, materialService.getEnabledMaterialList().size());
    }

    @Test
    public void testCreate_gapRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> materialService.createMaterial(material(),
                List.of(rule("0", "10", "2.0", "1.0"), rule("20", "0", "1.4", "0"))));
        assertEquals(CUSTOMIZATION_AREA_RULE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testCreate_overlapRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> materialService.createMaterial(material(),
                List.of(rule("0", "100", "2.0", "1.0"), rule("50", "0", "1.4", "0"))));
        assertEquals(CUSTOMIZATION_AREA_RULE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testCreate_lastMustBeOpenEnded() {
        ServiceException ex = assertThrows(ServiceException.class, () -> materialService.createMaterial(material(),
                List.of(rule("0", "10", "2.0", "1.0"))));
        assertEquals(CUSTOMIZATION_AREA_RULE_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testDisable_hidesFromEnabledList() {
        Long id = materialService.createMaterial(material(), List.of(rule("0", "0", "2.0", "1.0")));
        materialService.updateMaterialStatus(id, 1);
        assertEquals(0, materialService.getEnabledMaterialList().size());
        assertEquals(1, materialMapper.selectList().size());
    }

}
