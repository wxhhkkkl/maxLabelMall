package cn.iocoder.yudao.module.trade.enums.invoice;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * 发票**抬头类型**的枚举（Q38）
 *
 * 用来区分"开给公司"还是"开给个人" —— 两者的必填字段不同：
 * · {@link #COMPANY} 企业：抬头名称 + **纳税人识别号**（都必填）
 * · {@link #PERSONAL} 个人：**只要抬头名称**；税号**选填**
 *
 * ⚠️ 个人抬头的税号是**选填、不是禁止** —— 自然人可以用身份证号当纳税人识别号，
 * 写成"个人不许填"会把合法场景挡在门外（判定在 `InvoiceValidator`）。
 *
 * ⚠️ **个人抬头只能开普票**：增值税专用发票按税法只能开给一般纳税人企业，
 * 所以"个人 + 专票"这个组合会被 `InvoiceValidator` 直接拒掉。
 *
 * @author 芋道源码
 */
@AllArgsConstructor
@Getter
public enum InvoiceTitleKindEnum implements ArrayValuable<Integer> {

    /**
     * 企业（开给公司，需要税号）
     */
    COMPANY(1, "企业"),
    /**
     * 个人（开给自然人，只要名称）
     */
    PERSONAL(2, "个人"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(InvoiceTitleKindEnum::getKind).toArray(Integer[]::new);

    /**
     * 类型
     */
    private final Integer kind;
    /**
     * 名称
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static InvoiceTitleKindEnum valueOf(Integer kind) {
        return firstMatch(value -> value.getKind().equals(kind), values());
    }

}
