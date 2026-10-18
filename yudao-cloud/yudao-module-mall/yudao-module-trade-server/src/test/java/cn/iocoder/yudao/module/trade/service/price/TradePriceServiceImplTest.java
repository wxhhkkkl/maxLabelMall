package cn.iocoder.yudao.module.trade.service.price;

import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「本单算不算虚拟单」这条规则的单元测试（007 虚拟商品）
 *
 * ⚠️ 这个测试类**只覆盖 `resolveDeliveryType` 这个纯函数**，不是整个 `TradePriceServiceImpl`。
 * 本模块没有价格服务的测试基建（要 mock 商品/营销/会员一整套 API），为一条判定规则搭那套不划算；
 * 其余行为（虚拟单不计运费、免地址能建单、后台发货）走 quickstart 的手工清单，
 * 已在 `specs/007-virtual-goods/quickstart.md` 里**显式声明不做自动化**。
 *
 * 为什么这条规则值得自动化：它决定"**要不要收货地址**" —— 判错的后果是
 * 实物订单不收地址（发不出去）或虚拟订单强要地址（买不了）。
 *
 * 纯函数测试：不需要 Spring、不需要 H2。
 */
public class TradePriceServiceImplTest {

    private ProductSpuRespDTO spuOf(Integer... deliveryTypes) {
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setDeliveryTypes(Arrays.asList(deliveryTypes));
        return spu;
    }

    // ==================== 整单虚拟 ====================

    @Test
    public void testResolveDeliveryType_全部商品都只支持虚拟商品_判为虚拟() {
        List<ProductSpuRespDTO> spuList = List.of(
                spuOf(DeliveryTypeEnum.VIRTUAL.getType()),
                spuOf(DeliveryTypeEnum.VIRTUAL.getType()));

        assertEquals(DeliveryTypeEnum.VIRTUAL.getType(), TradePriceServiceImpl.resolveDeliveryType(spuList));
    }

    // ==================== 有实物就是快递 ====================

    @Test
    public void testResolveDeliveryType_混买_判为快递() {
        // 软件 + 标签纸：实物要寄，所以整单按快递（会要收货地址）
        List<ProductSpuRespDTO> spuList = List.of(
                spuOf(DeliveryTypeEnum.VIRTUAL.getType()),
                spuOf(DeliveryTypeEnum.EXPRESS.getType()));

        assertEquals(DeliveryTypeEnum.EXPRESS.getType(), TradePriceServiceImpl.resolveDeliveryType(spuList));
    }

    @Test
    public void testResolveDeliveryType_普通实物_判为快递() {
        assertEquals(DeliveryTypeEnum.EXPRESS.getType(),
                TradePriceServiceImpl.resolveDeliveryType(List.of(spuOf(DeliveryTypeEnum.EXPRESS.getType()))));
        // 支持"快递 + 自提"的实物商品同样是快递
        assertEquals(DeliveryTypeEnum.EXPRESS.getType(),
                TradePriceServiceImpl.resolveDeliveryType(
                        List.of(spuOf(DeliveryTypeEnum.EXPRESS.getType(), DeliveryTypeEnum.PICK_UP.getType()))));
        // 只支持自提的，也按快递走（本次不改自提，保守）
        assertEquals(DeliveryTypeEnum.EXPRESS.getType(),
                TradePriceServiceImpl.resolveDeliveryType(List.of(spuOf(DeliveryTypeEnum.PICK_UP.getType()))));
    }

    // ==================== 两个刻意保守的边界 ====================

    @Test
    public void testResolveDeliveryType_配送方式为空_判为快递() {
        // ⚠️ 历史数据里 `delivery_types` 的建表默认值就是空串，这类商品不少见。
        // 把它们当成虚拟 → 那些**实物**商品会变成"不要收货地址"。所以空 → 快递。
        assertEquals(DeliveryTypeEnum.EXPRESS.getType(),
                TradePriceServiceImpl.resolveDeliveryType(List.of(spuOf())));
        assertEquals(DeliveryTypeEnum.EXPRESS.getType(), TradePriceServiceImpl.resolveDeliveryType(
                List.of(new ProductSpuRespDTO())));
    }

    @Test
    public void testResolveDeliveryType_同时勾了快递与虚拟商品_判为快递() {
        // 它支持快递，就该要地址 —— 不能因为"含 3"就当虚拟
        assertEquals(DeliveryTypeEnum.EXPRESS.getType(), TradePriceServiceImpl.resolveDeliveryType(
                List.of(spuOf(DeliveryTypeEnum.EXPRESS.getType(), DeliveryTypeEnum.VIRTUAL.getType()))));
    }

}
