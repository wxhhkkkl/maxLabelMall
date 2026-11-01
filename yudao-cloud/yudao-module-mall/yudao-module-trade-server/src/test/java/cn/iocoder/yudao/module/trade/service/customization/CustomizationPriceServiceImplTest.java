package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationAreaRuleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationMaterialDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationAreaRuleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationConfigMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationMaterialMapper;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationPriceResultBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationSpecBO;
import cn.iocoder.yudao.module.trade.service.price.TradePriceService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 定制计价单测（014 / Q91~Q105）
 *
 * 口径：面积按 ㎡；打印 = 基价 × 材质打印系数 × 区间打印系数 × 整单总面积，再与初始价取大。
 */
@Import({CustomizationPriceServiceImpl.class, CustomizationMaterialServiceImpl.class,
        CustomizationConfigServiceImpl.class, CustomizationSpecValidator.class})
public class CustomizationPriceServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CustomizationPriceService priceService;
    @Resource
    private CustomizationMaterialMapper materialMapper;
    @Resource
    private CustomizationAreaRuleMapper areaRuleMapper;
    @Resource
    private CustomizationConfigMapper configMapper;

    /** 纯计价不走运费链路；这里只是满足注入 */
    @MockitoBean
    private TradePriceService tradePriceService;

    private Long materialId;

    @BeforeEach
    public void setUp() {
        materialId = createMaterial();
        configMapper.insert(CustomizationConfigServiceImpl.defaultConfig());
    }

    private Long createMaterial() {
        CustomizationMaterialDO material = CustomizationMaterialDO.builder()
                .name("可移除铜版").basePrice(1138)
                .printCoefficient(new BigDecimal("4.0000"))
                .printingCoefficient(new BigDecimal("1.5000"))
                .minQuantity(1).maxQuantity(100000)
                .minWidth(10).maxWidth(2000).minHeight(10).maxHeight(2000)
                .status(0).sort(0).build();
        materialMapper.insert(material);
        insertRule(material.getId(), "0", "10", "2.0000", "1.0000");
        insertRule(material.getId(), "10", "0", "1.4000", "0.0000");
        return material.getId();
    }

    private void insertRule(Long materialId, String min, String max, String print, String dieCut) {
        CustomizationAreaRuleDO rule = CustomizationAreaRuleDO.builder()
                .materialId(materialId)
                .minArea(new BigDecimal(min)).maxArea(new BigDecimal(max))
                .printCoefficient(new BigDecimal(print)).dieCutCoefficient(new BigDecimal(dieCut))
                .sort(0).build();
        areaRuleMapper.insert(rule);
    }

    private CustomizationSpecBO spec(int width, int height, int quantity) {
        CustomizationSpecBO spec = new CustomizationSpecBO();
        spec.setMaterialId(materialId);
        spec.setWidth(width);
        spec.setHeight(height);
        spec.setQuantity(quantity);
        spec.setMode(2);
        spec.setDieCut(false);
        spec.setUrgent(false);
        return spec;
    }

    @Test
    public void testCalculate_printOnly_doubleCoefficientAndTotalArea() {
        // 50×100mm = 0.005㎡；1000 张 = 5㎡ → 0-10 档（系数 2.0）
        // 1138 × 4 × 2.0 × 5 = 45520 分
        CustomizationPriceResultBO result = priceService.calculate(spec(50, 100, 1000));
        assertEquals(0, new BigDecimal("0.0050").compareTo(result.getQuote().getUnitArea()));
        assertEquals(0, new BigDecimal("5.0000").compareTo(result.getQuote().getTotalArea()));
        assertEquals(45520, result.getQuote().getBreakdown().getPrint());
        assertEquals(45520, result.getQuote().getBreakdown().getTotal());
        assertNotNull(result.getQuote().getParamSnapshot());
    }

    @Test
    public void testCalculate_minimumCharge() {
        // 10×10mm = 0.0001㎡ × 1 张：计算价不足 1 分，按打印初始价 20000 分
        CustomizationPriceResultBO result = priceService.calculate(spec(10, 10, 1));
        assertEquals(20000, result.getQuote().getBreakdown().getPrint());
        assertEquals(20000, result.getQuote().getBreakdown().getTotal());
    }

    @Test
    public void testCalculate_boundaryBelongsToHigherTier() {
        // 100×100mm = 0.01㎡ × 1000 张 = 10㎡，恰好落在 10-0 档（左闭右开）
        CustomizationPriceResultBO result = priceService.calculate(spec(100, 100, 1000));
        assertEquals(0, new BigDecimal("10.0000").compareTo(result.getQuote().getTotalArea()));
        // 1138 × 4 × 1.4 × 10 = 63728 分
        assertEquals(63728, result.getQuote().getBreakdown().getPrint());
    }

    @Test
    public void testCalculate_dieCutCoefficientZeroStillChargesBase() {
        // 10-0 档的模切系数是 0：计算价 0，但仍收模切初始价 40000（Q105=B）
        CustomizationSpecBO spec = spec(100, 100, 1000);
        spec.setDieCut(true);
        CustomizationPriceResultBO result = priceService.calculate(spec);
        assertEquals(40000, result.getQuote().getBreakdown().getDieCut());
        assertEquals(63728 + 40000, result.getQuote().getBreakdown().getTotal());
    }

    @Test
    public void testCalculate_urgentUsesSubtotalAndBaseFloor() {
        // 小计 45520 → 加急计算价 4552，低于加急初始价 5000 → 收 5000
        CustomizationSpecBO spec = spec(50, 100, 1000);
        spec.setUrgent(true);
        CustomizationPriceResultBO result = priceService.calculate(spec);
        assertEquals(5000, result.getQuote().getBreakdown().getUrgent());
        assertEquals(45520 + 5000, result.getQuote().getBreakdown().getTotal());
    }

    @Test
    public void testCalculate_printingModeOnlyChargesPrinting() {
        // 模式 1 = 标签印刷（多色）：只收印刷，不收打印（Q108）
        CustomizationSpecBO spec = spec(50, 100, 1000);
        spec.setMode(1);
        CustomizationPriceResultBO result = priceService.calculate(spec);
        // 1138 × 1.5 × 5 = 8535，低于印刷初始价 60000 → 60000
        assertEquals(0, result.getQuote().getBreakdown().getPrint());
        assertEquals(60000, result.getQuote().getBreakdown().getPrinting());
        assertEquals(60000, result.getQuote().getBreakdown().getTotal());
    }

    @Test
    public void testCalculate_bothModeChargesBoth() {
        CustomizationSpecBO spec = spec(50, 100, 1000);
        spec.setMode(3);
        CustomizationPriceResultBO result = priceService.calculate(spec);
        assertEquals(45520, result.getQuote().getBreakdown().getPrint());
        assertEquals(60000, result.getQuote().getBreakdown().getPrinting());
        assertEquals(105520, result.getQuote().getBreakdown().getTotal());
    }

}
