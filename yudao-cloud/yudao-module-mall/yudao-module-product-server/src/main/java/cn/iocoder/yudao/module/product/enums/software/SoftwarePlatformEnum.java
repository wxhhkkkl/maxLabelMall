package cn.iocoder.yudao.module.product.enums.software;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * 软件平台枚举（013）
 *
 * 与桌面端上报的 platform 字段逐字一致。
 */
@AllArgsConstructor
@Getter
public enum SoftwarePlatformEnum implements ArrayValuable<String> {

    WINDOWS("windows", "Windows"),
    MACOS("macos", "macOS"),
    LINUX("linux", "Linux"),
    ;

    public static final String[] ARRAYS = Arrays.stream(values()).map(SoftwarePlatformEnum::getPlatform).toArray(String[]::new);

    private final String platform;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static SoftwarePlatformEnum parse(String platform) {
        return firstMatch(value -> value.getPlatform().equals(platform), values());
    }

}
