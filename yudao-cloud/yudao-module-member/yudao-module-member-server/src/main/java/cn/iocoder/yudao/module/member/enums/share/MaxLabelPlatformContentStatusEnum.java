package cn.iocoder.yudao.module.member.enums.share;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * MaxLabel 平台内容状态枚举
 */
@AllArgsConstructor
@Getter
public enum MaxLabelPlatformContentStatusEnum implements ArrayValuable<Integer> {

    ONLINE(1, "上架"),
    OFFLINE(0, "下架"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MaxLabelPlatformContentStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static MaxLabelPlatformContentStatusEnum valueOf(Integer status) {
        return firstMatch(value -> value.getStatus().equals(status), values());
    }

}
