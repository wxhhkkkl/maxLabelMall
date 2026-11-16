package cn.iocoder.yudao.module.product.enums.spu;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 商品浏览等级模式
 *
 * 特性 019：与会员等级关联的商品可见性。
 *
 * @author MaxLabel
 */
@Getter
@AllArgsConstructor
public enum ProductSpuBrowseLevelTypeEnum implements ArrayValuable<Integer> {

    PUBLIC(0, "公开"),
    LOGIN(1, "全部登录会员"),
    LEVEL(2, "指定等级会员");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(ProductSpuBrowseLevelTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 模式
     */
    private final Integer type;
    /**
     * 模式名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static ProductSpuBrowseLevelTypeEnum valueOf(Integer type) {
        return Arrays.stream(values())
                .filter(item -> item.getType().equals(type))
                .findFirst().orElse(null);
    }

    public static boolean isValid(Integer type) {
        return valueOf(type) != null;
    }

    public static boolean isLevel(Integer type) {
        return LEVEL.getType().equals(type);
    }

}
