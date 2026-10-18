package cn.iocoder.yudao.module.trade.enums.invoice;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * 发票类型（票种）的枚举
 *
 * ⚠️ **只有两种，且都是电子票**（specs/006-order-invoice 的 Q27）：本特性
 * **不提供纸质发票、也不涉及邮寄** —— 所以没有任何"配送方式 / 收件地址"一类的字段或枚举。
 *
 * 两种票的**字段要求不同**（Q27，判定在 `InvoiceValidator`）：
 * · {@link #E_NORMAL} 普票：抬头名称必填 + 税号选填（填了要合法）
 * · {@link #E_SPECIAL} 专票：抬头名称、税号、地址、电话、开户行及账号**全部必填**
 *
 * @author 芋道源码
 */
@AllArgsConstructor
@Getter
public enum InvoiceTypeEnum implements ArrayValuable<Integer> {

    /**
     * 电子普通发票
     */
    E_NORMAL(1, "电子普通发票"),
    /**
     * 电子增值税专用发票
     */
    E_SPECIAL(2, "电子增值税专用发票"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(InvoiceTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 类型名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static InvoiceTypeEnum valueOf(Integer type) {
        return firstMatch(value -> value.getType().equals(type), values());
    }

}
