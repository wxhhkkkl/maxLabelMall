package cn.iocoder.yudao.module.member.enums.share;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * MaxLabel 共享空间类型枚举
 */
@AllArgsConstructor
@Getter
public enum MaxLabelShareScopeEnum implements ArrayValuable<Integer> {

    PERSONAL(1, "个人空间"),
    ENTERPRISE(2, "企业空间"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MaxLabelShareScopeEnum::getScope).toArray(Integer[]::new);

    private final Integer scope;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static MaxLabelShareScopeEnum valueOf(Integer scope) {
        return firstMatch(value -> value.getScope().equals(scope), values());
    }

}
