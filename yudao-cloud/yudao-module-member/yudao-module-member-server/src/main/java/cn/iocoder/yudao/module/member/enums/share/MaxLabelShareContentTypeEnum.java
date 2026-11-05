package cn.iocoder.yudao.module.member.enums.share;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * MaxLabel 共享内容类型枚举
 */
@AllArgsConstructor
@Getter
public enum MaxLabelShareContentTypeEnum implements ArrayValuable<Integer> {

    TEMPLATE(1, "模板", "msdx"),
    TXT(2, "纯文本", "txt"),
    EXCEL(3, "表格", "xls,xlsx"),
    CSV(4, "CSV", "csv"),
    IMAGE(5, "图片", "png,jpg,jpeg,gif,bmp,webp"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MaxLabelShareContentTypeEnum::getType).toArray(Integer[]::new);

    private final Integer type;
    private final String name;
    private final String extensions;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static MaxLabelShareContentTypeEnum valueOf(Integer type) {
        return firstMatch(value -> value.getType().equals(type), values());
    }

}
