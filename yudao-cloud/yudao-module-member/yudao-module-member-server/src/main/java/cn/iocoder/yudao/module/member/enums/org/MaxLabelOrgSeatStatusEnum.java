package cn.iocoder.yudao.module.member.enums.org;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * MaxLabel 企业席位状态枚举
 */
@AllArgsConstructor
@Getter
public enum MaxLabelOrgSeatStatusEnum implements ArrayValuable<Integer> {

    ACTIVE(0, "有效"),
    REVOKED(1, "已撤销"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MaxLabelOrgSeatStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static MaxLabelOrgSeatStatusEnum valueOf(Integer status) {
        return firstMatch(value -> value.getStatus().equals(status), values());
    }

}
