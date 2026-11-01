package cn.iocoder.yudao.module.trade.service.price.calculator;

import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 定制单的订单项金额落法（014 / R90 / T057）
 *
 * 隐藏 SKU 的真实价格是 0，**订单项金额只能来自 `customTotalPrice`**。
 * 这条分支一旦回归，定制单会静默地按 0 元建单 —— 所以把取整与差额逐分钉住。
 */
public class TradePriceCalculatorHelperCustomizationTest {

    private static final Long SPU_ID = 990001L;
    private static final Long SKU_ID = 990002L;

    private TradePriceCalculateRespBO calculate(Integer customTotalPrice, int count, int skuPrice) {
        TradePriceCalculateReqBO reqBO = new TradePriceCalculateReqBO();
        reqBO.setPointStatus(false);
        TradePriceCalculateReqBO.Item item = new TradePriceCalculateReqBO.Item();
        item.setSkuId(SKU_ID);
        item.setCount(count);
        item.setSelected(true);
        item.setCustomTotalPrice(customTotalPrice);
        reqBO.setItems(List.of(item));
        return TradePriceCalculatorHelper.buildCalculateResp(reqBO, List.of(buildSpu()), List.of(buildSku(skuPrice)));
    }

    private static ProductSpuRespDTO buildSpu() {
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setId(SPU_ID);
        spu.setName("印刷定制服务（系统）");
        spu.setDeliveryTypes(List.of(1));
        return spu;
    }

    private static ProductSkuRespDTO buildSku(int price) {
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(SKU_ID);
        sku.setSpuId(SPU_ID);
        sku.setPrice(price);
        return sku;
    }

    @Test
    public void testCustomTotalPrice_unitPriceRoundsUp_discountAbsorbsRemainder() {
        // 455.20 元 ÷ 1000 张 = 45.52 分 → 单价向上取整 46，差额 0.48 元记入商品优惠
        TradePriceCalculateRespBO result = calculate(45520, 1000, 0);

        TradePriceCalculateRespBO.OrderItem item = result.getItems().get(0);
        assertEquals(1000, item.getCount());
        assertEquals(46, item.getPrice());
        assertEquals(480, item.getDiscountPrice());
        assertEquals(45520, item.getPayPrice());
        assertEquals(46000, item.getPrice() * item.getCount());
        // 整单合计必须**严格等于报价**，不能被取整放大
        assertEquals(45520, result.getPrice().getPayPrice());
        assertEquals(480, result.getPrice().getDiscountPrice());
    }

    @Test
    public void testCustomTotalPrice_divisible_hasNoDiscount() {
        TradePriceCalculateRespBO result = calculate(46000, 1000, 0);

        TradePriceCalculateRespBO.OrderItem item = result.getItems().get(0);
        assertEquals(46, item.getPrice());
        assertEquals(0, item.getDiscountPrice());
        assertEquals(46000, item.getPayPrice());
    }

    @Test
    public void testCustomTotalPrice_oddCount_stillEqualsQuote() {
        // 100 分 ÷ 3 张 → 单价 34，差额 2 分
        TradePriceCalculateRespBO result = calculate(100, 3, 0);

        TradePriceCalculateRespBO.OrderItem item = result.getItems().get(0);
        assertEquals(34, item.getPrice());
        assertEquals(2, item.getDiscountPrice());
        assertEquals(100, item.getPayPrice());
        assertEquals(100, result.getPrice().getPayPrice());
    }

    @Test
    public void testNoCustomTotalPrice_usesSkuPrice_unchanged() {
        // 回归护栏：普通商品（没有 customTotalPrice）仍按 SKU 单价 × 数量
        TradePriceCalculateRespBO result = calculate(null, 3, 1200);

        TradePriceCalculateRespBO.OrderItem item = result.getItems().get(0);
        assertEquals(1200, item.getPrice());
        assertEquals(0, item.getDiscountPrice());
        assertEquals(3600, item.getPayPrice());
        assertEquals(3600, result.getPrice().getPayPrice());
    }

}
