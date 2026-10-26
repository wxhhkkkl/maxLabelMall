package cn.iocoder.yudao.module.product.enums.subscriptionplan;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * MaxLabel 会员授权商品类别枚举
 *
 * 个人计划与个人会员绑定；企业计划按席位售卖，可由企业分配给成员。
 */
@AllArgsConstructor
@Getter
public enum MaxLabelSubscriptionScopeEnum implements ArrayValuable<Integer> {

    PERSONAL(1, "个人"),
    ENTERPRISE(2, "企业"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MaxLabelSubscriptionScopeEnum::getScope).toArray(Integer[]::new);

    private final Integer scope;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static MaxLabelSubscriptionScopeEnum valueOf(Integer scope) {
        return firstMatch(value -> value.getScope().equals(scope), values());
    }

}
