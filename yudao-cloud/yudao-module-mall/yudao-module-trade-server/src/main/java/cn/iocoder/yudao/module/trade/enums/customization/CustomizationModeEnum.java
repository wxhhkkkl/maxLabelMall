package cn.iocoder.yudao.module.trade.enums.customization;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 印刷定制的业务模式（014 / 按参考页面三选一，Q108）
 *
 * · 标签印刷（多色）：只收印刷费
 * · 标签打印（黑色）：只收打印费
 * · 多色印刷+黑色打印：两项都收
 */
@Getter
@AllArgsConstructor
public enum CustomizationModeEnum implements ArrayValuable<Integer> {

    PRINTING(1, "标签印刷（多色）"),
    PRINT(2, "标签打印（黑色）"),
    BOTH(3, "多色印刷+黑色打印"),
    /** 定制空白标签：不印刷也不打印，但仍有材料与加工成本（按打印项计价） */
    BLANK(4, "定制空白标签"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CustomizationModeEnum::getMode).toArray(Integer[]::new);

    /** 模式值 */
    private final Integer mode;
    /** 展示名 */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    /** 是否需要收「打印（黑色）」 */
    public static boolean needPrint(Integer mode) {
        return PRINT.getMode().equals(mode) || BOTH.getMode().equals(mode)
                || BLANK.getMode().equals(mode);
    }

    /** 是否需要收「印刷（多色）」 */
    public static boolean needPrinting(Integer mode) {
        return PRINTING.getMode().equals(mode) || BOTH.getMode().equals(mode);
    }

    public static String nameOf(Integer mode) {
        for (CustomizationModeEnum value : values()) {
            if (value.getMode().equals(mode)) {
                return value.getName();
            }
        }
        return "";
    }

}
