package cn.iocoder.yudao.module.trade.enums.invoice;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * 票种（发票 / 收据）的枚举
 *
 * ⚠️ **全部都是电子票，不提供纸质发票、也不涉及邮寄**（specs/006-order-invoice 的 Q27）。
 *
 * 字段要求（判定在 `InvoiceValidator`）：
 * · {@link #E_NORMAL} 普票：企业「名称 + 税号」、个人「只要名称」
 * · {@link #E_SPECIAL} 专票：企业「名称 + 税号」；个人不能开专票
 * · {@link #RECEIPT} 收据：**只要名称**（企业/个人都一样，Q61）；不是税票，不要求税号
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
    /**
     * 收据（Q61：不是税票，只要抬头名称；企业 / 个人都能开）
     */
    RECEIPT(3, "收据"),
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
