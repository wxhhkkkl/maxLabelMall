package cn.iocoder.yudao.module.product.enums.software;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * 软件类型枚举（013 软件与驱动分发）
 *
 * 桌面端软件参与客户端更新判定；打印驱动只用于对外下载。
 */
@AllArgsConstructor
@Getter
public enum SoftwareTypeEnum implements ArrayValuable<Integer> {

    DESKTOP(1, "桌面端软件"),
    DRIVER(2, "打印驱动"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(SoftwareTypeEnum::getType).toArray(Integer[]::new);

    private final Integer type;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static SoftwareTypeEnum parse(Integer type) {
        return firstMatch(value -> value.getType().equals(type), values());
    }

}
