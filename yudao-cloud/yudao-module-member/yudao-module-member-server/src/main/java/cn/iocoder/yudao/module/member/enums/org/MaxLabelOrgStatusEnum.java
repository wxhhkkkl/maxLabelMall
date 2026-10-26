package cn.iocoder.yudao.module.member.enums.org;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * MaxLabel 企业状态枚举
 */
@AllArgsConstructor
@Getter
public enum MaxLabelOrgStatusEnum implements ArrayValuable<Integer> {

    ENABLE(0, "启用"),
    DISABLE(1, "停用"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MaxLabelOrgStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static MaxLabelOrgStatusEnum valueOf(Integer status) {
        return firstMatch(value -> value.getStatus().equals(status), values());
    }

}
