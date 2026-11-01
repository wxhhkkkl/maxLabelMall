package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationAreaRuleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationMaterialDO;
import cn.iocoder.yudao.module.trade.enums.customization.CustomizationModeEnum;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationPriceResultBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationQuoteBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationSpecBO;
import cn.iocoder.yudao.module.trade.service.price.TradePriceService;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_AREA_NOT_MATCHED;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.HIDDEN_SKU_ID;

/**
 * 定制计价实现（014 / Q91~Q105）
 *
 * 口径（Q91=C / Q102 / Q103 / Q104 / Q105）：
 * · 面积：unit = 宽(mm) × 高(mm) ÷ 1,000,000（㎡，4 位小数）；total = unit × 数量
 * · 打印：基价 × 材质打印系数 × 区间打印系数 × total，再与打印初始价取大
 * · 印刷：基价 × 印刷系数 × total，再与印刷初始价取大（选了才有）
 * · 模切：基价 × 区间模切系数 × total，再与模切初始价取大（选了才有；系数 0 也收初始价）
 * · 加急：小计 × (加急系数 − 1)，再与加急初始价取大（选了才有）
 */
@Service
public class CustomizationPriceServiceImpl implements CustomizationPriceService {

    @Resource
    private CustomizationMaterialService customizationMaterialService;
    @Resource
    private CustomizationConfigService customizationConfigService;
    @Resource
    private CustomizationSpecValidator specValidator;
    @Resource
    private TradePriceService tradePriceService;

    @Override
    public CustomizationPriceResultBO calculate(CustomizationSpecBO spec) {
        CustomizationMaterialDO material = customizationMaterialService.validateEnabledMaterial(spec.getMaterialId());
        specValidator.validate(spec, material);
        List<CustomizationAreaRuleDO> rules = customizationMaterialService.getAreaRules(material.getId());
        CustomizationConfigDO config = customizationConfigService.getConfigOrDefault();

        BigDecimal unitArea = BigDecimal.valueOf(spec.getWidth())
                .multiply(BigDecimal.valueOf(spec.getHeight()))
                .divide(BigDecimal.valueOf(1_000_000L), 4, RoundingMode.HALF_UP);
        BigDecimal totalArea = unitArea.multiply(BigDecimal.valueOf(spec.getQuantity()))
                .setScale(4, RoundingMode.HALF_UP);

        CustomizationAreaRuleDO areaRule = matchAreaRule(rules, totalArea);
        if (areaRule == null) {
            throw exception(CUSTOMIZATION_AREA_NOT_MATCHED);
        }

        BigDecimal basePrice = BigDecimal.valueOf(material.getBasePrice());
        // 业务模式三选一（Q108）：印刷 / 打印 / 两者，各自只收对应项
        int print = 0;
        if (CustomizationModeEnum.needPrint(spec.getMode())) {
            int printCalc = toFen(basePrice
                    .multiply(material.getPrintCoefficient())
                    .multiply(areaRule.getPrintCoefficient())
                    .multiply(totalArea));
            print = Math.max(config.getPrintBasePrice(), printCalc);
        }

        int printing = 0;
        if (CustomizationModeEnum.needPrinting(spec.getMode())) {
            int calc = toFen(basePrice
                    .multiply(material.getPrintingCoefficient())
                    .multiply(totalArea));
            printing = Math.max(config.getPrintingBasePrice(), calc);
        }

        int dieCut = 0;
        if (Boolean.TRUE.equals(spec.getDieCut())) {
            int calc = toFen(basePrice
                    .multiply(areaRule.getDieCutCoefficient())
                    .multiply(totalArea));
            dieCut = Math.max(config.getDieCutBasePrice(), calc);
        }

        int subtotal = print + printing + dieCut;
        int urgent = 0;
        if (Boolean.TRUE.equals(spec.getUrgent())) {
            int calc = toFen(BigDecimal.valueOf(subtotal)
                    .multiply(config.getUrgentCoefficient().subtract(BigDecimal.ONE)));
            urgent = Math.max(config.getUrgentBasePrice(), calc);
        }
        int total = subtotal + urgent;

        CustomizationQuoteBO quote = new CustomizationQuoteBO();
        quote.setMaterialId(material.getId());
        quote.setMaterialName(material.getName());
        quote.setUnitArea(unitArea);
        quote.setTotalArea(totalArea);
        CustomizationQuoteBO.Breakdown breakdown = new CustomizationQuoteBO.Breakdown();
        breakdown.setPrint(print);
        breakdown.setPrinting(printing);
        breakdown.setDieCut(dieCut);
        breakdown.setUrgent(urgent);
        breakdown.setSubtotal(subtotal);
        breakdown.setTotal(total);
        quote.setBreakdown(breakdown);
        quote.setFreight(0);
        quote.setPayable(total);
        quote.setQuoteTime(LocalDateTime.now());
        quote.setParamSnapshot(buildSnapshot(material, areaRule, config, unitArea, totalArea, breakdown));

        CustomizationPriceResultBO result = new CustomizationPriceResultBO();
        result.setMaterial(material);
        result.setAreaRule(areaRule);
        result.setConfig(config);
        result.setQuote(quote);
        return result;
    }

    @Override
    public CustomizationQuoteBO quote(CustomizationSpecBO spec, Long userId, Long addressId, Integer deliveryType) {
        CustomizationPriceResultBO result = calculate(spec);
        CustomizationQuoteBO quote = result.getQuote();
        if (addressId != null && userId != null) {
            TradePriceCalculateRespBO resp = tradePriceService.calculateOrderPrice(
                    buildCalculateReq(result, spec.getQuantity(), userId, addressId, deliveryType));
            quote.setFreight(defaultInt(resp.getPrice().getDeliveryPrice()));
            quote.setPayable(defaultInt(resp.getPrice().getPayPrice()));
        }
        return quote;
    }

    @Override
    public TradePriceCalculateRespBO calculateOrderPrice(Long userId, CustomizationSpecBO spec) {
        CustomizationPriceResultBO result = calculate(spec);
        TradePriceCalculateRespBO resp = tradePriceService.calculateOrderPrice(
                buildCalculateReq(result, spec.getQuantity(), userId, null, null));
        if (resp.getItems() != null && !resp.getItems().isEmpty()) {
            // 订单列表与支付主题需要可读的名称（隐藏 SKU 的名字是「印刷定制服务（系统）」）
            resp.getItems().get(0).setSpuName("印刷定制 · " + result.getQuote().getMaterialName());
        }
        return resp;
    }

    private TradePriceCalculateReqBO buildCalculateReq(CustomizationPriceResultBO result, Integer quantity,
                                                       Long userId, Long addressId, Integer deliveryType) {
        CustomizationQuoteBO quote = result.getQuote();
        TradePriceCalculateReqBO reqBO = new TradePriceCalculateReqBO();
        reqBO.setUserId(userId);
        reqBO.setPointStatus(false);
        reqBO.setAddressId(addressId);
        reqBO.setDeliveryType(deliveryType != null ? deliveryType : DeliveryTypeEnum.EXPRESS.getType());
        TradePriceCalculateReqBO.Item item = new TradePriceCalculateReqBO.Item();
        item.setSkuId(HIDDEN_SKU_ID);
        item.setCount(quantity == null ? 1 : quantity);
        item.setSelected(true);
        item.setCustomTotalPrice(quote.getBreakdown().getTotal());
        reqBO.setItems(List.of(item));
        return reqBO;
    }

    /**
     * 整单总面积匹配区间：左闭右开；maxArea = 0 表示无上限（Q94 / Q103）
     */
    static CustomizationAreaRuleDO matchAreaRule(List<CustomizationAreaRuleDO> rules, BigDecimal totalArea) {
        if (rules == null) {
            return null;
        }
        for (CustomizationAreaRuleDO rule : rules) {
            boolean lower = totalArea.compareTo(rule.getMinArea()) >= 0;
            boolean upper = rule.getMaxArea().compareTo(BigDecimal.ZERO) == 0
                    || totalArea.compareTo(rule.getMaxArea()) < 0;
            if (lower && upper) {
                return rule;
            }
        }
        return null;
    }

    private static int toFen(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static String buildSnapshot(CustomizationMaterialDO material, CustomizationAreaRuleDO rule,
                                        CustomizationConfigDO config, BigDecimal unitArea, BigDecimal totalArea,
                                        CustomizationQuoteBO.Breakdown breakdown) {
        Map<String, Object> materialSnapshot = new LinkedHashMap<>();
        materialSnapshot.put("id", material.getId());
        materialSnapshot.put("name", material.getName());
        materialSnapshot.put("basePrice", material.getBasePrice());
        materialSnapshot.put("printCoefficient", material.getPrintCoefficient());
        materialSnapshot.put("printingCoefficient", material.getPrintingCoefficient());

        Map<String, Object> ruleSnapshot = new LinkedHashMap<>();
        ruleSnapshot.put("id", rule.getId());
        ruleSnapshot.put("minArea", rule.getMinArea());
        ruleSnapshot.put("maxArea", rule.getMaxArea());
        ruleSnapshot.put("printCoefficient", rule.getPrintCoefficient());
        ruleSnapshot.put("dieCutCoefficient", rule.getDieCutCoefficient());

        Map<String, Object> configSnapshot = new LinkedHashMap<>();
        configSnapshot.put("printBasePrice", config.getPrintBasePrice());
        configSnapshot.put("printingBasePrice", config.getPrintingBasePrice());
        configSnapshot.put("dieCutBasePrice", config.getDieCutBasePrice());
        configSnapshot.put("urgentBasePrice", config.getUrgentBasePrice());
        configSnapshot.put("urgentCoefficient", config.getUrgentCoefficient());

        Map<String, Object> computed = new LinkedHashMap<>();
        computed.put("unitArea", unitArea);
        computed.put("totalArea", totalArea);
        computed.put("print", breakdown.getPrint());
        computed.put("printing", breakdown.getPrinting());
        computed.put("dieCut", breakdown.getDieCut());
        computed.put("urgent", breakdown.getUrgent());
        computed.put("subtotal", breakdown.getSubtotal());
        computed.put("total", breakdown.getTotal());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("material", materialSnapshot);
        snapshot.put("areaRule", ruleSnapshot);
        snapshot.put("config", configSnapshot);
        snapshot.put("computed", computed);
        return JsonUtils.toJsonString(snapshot);
    }

}
