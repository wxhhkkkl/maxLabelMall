package cn.iocoder.yudao.module.member.enums.share;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * MaxLabel 共享内容存储方式枚举
 */
@AllArgsConstructor
@Getter
public enum MaxLabelShareStorageTypeEnum implements ArrayValuable<Integer> {

    TEXT(1, "文本正文"),
    FILE(2, "文件地址"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MaxLabelShareStorageTypeEnum::getStorageType).toArray(Integer[]::new);

    private final Integer storageType;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static MaxLabelShareStorageTypeEnum valueOf(Integer storageType) {
        return firstMatch(value -> value.getStorageType().equals(storageType), values());
    }

}
