package cn.iocoder.yudao.module.trade.enums.delivery;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 配送方式枚举
 *
 * ⚠️ 管理端**商品编辑**里的「配送方式」选项**不是**取自本枚举，而是系统字典
 * `trade_delivery_type` —— 新增一档时必须同时插一条字典数据（见 `011-virtual-goods-dict.sql`）。
 *
 * @author 芋道源码
 */
@Getter
@AllArgsConstructor
public enum DeliveryTypeEnum implements ArrayValuable<Integer> {

    EXPRESS(1, "快递发货"),
    PICK_UP(2, "用户自提"),
    /**
     * 虚拟商品（软件等）：**不涉及物流** —— 下单不需要收货地址、不产生运费。
     *
     * ⚠️ 判定"整单虚拟"的条件是**所有商品都只支持虚拟商品**，见 `TradePriceServiceImpl#resolveDeliveryType`。
     * 一个同时勾了「快递发货」和「虚拟商品」的商品会被当成**快递**（更保守，会要地址）。
     */
    VIRTUAL(3, "虚拟商品"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DeliveryTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 配送方式
     */
    private final Integer type;
    /**
     * 状态名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
