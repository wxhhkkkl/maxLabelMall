package cn.iocoder.yudao.module.member.enums.org;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * MaxLabel 企业成员角色枚举
 *
 * 角色只决定管理权限，**不**自动附带权益（FR-306）。
 */
@AllArgsConstructor
@Getter
public enum MaxLabelOrgRoleEnum implements ArrayValuable<Integer> {

    OWNER(1, "所有者"),
    ADMIN(2, "管理员"),
    MEMBER(3, "普通成员"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MaxLabelOrgRoleEnum::getRole).toArray(Integer[]::new);

    private final Integer role;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static MaxLabelOrgRoleEnum valueOf(Integer role) {
        return firstMatch(value -> value.getRole().equals(role), values());
    }

    /** OWNER 或 ADMIN：可管理成员与席位 */
    public static boolean isAdmin(Integer role) {
        return OWNER.getRole().equals(role) || ADMIN.getRole().equals(role);
    }

}
