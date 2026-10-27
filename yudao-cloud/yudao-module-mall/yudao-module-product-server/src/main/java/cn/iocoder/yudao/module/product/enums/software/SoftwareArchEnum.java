package cn.iocoder.yudao.module.product.enums.software;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * 软件架构枚举（013）
 *
 * 与桌面端上报的 arch 字段逐字一致。
 */
@AllArgsConstructor
@Getter
public enum SoftwareArchEnum implements ArrayValuable<String> {

    X64("x64", "x64"),
    ARM64("arm64", "arm64"),
    X86("x86", "x86"),
    ;

    public static final String[] ARRAYS = Arrays.stream(values()).map(SoftwareArchEnum::getArch).toArray(String[]::new);

    private final String arch;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static SoftwareArchEnum parse(String arch) {
        return firstMatch(value -> value.getArch().equals(arch), values());
    }

}
