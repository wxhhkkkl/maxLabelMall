package cn.iocoder.yudao.module.trade.enums.customerservice;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户服务工单来源（021 / Q2）
 */
@Getter
@AllArgsConstructor
public enum CustomerServiceSourceEnum {

    MEMBER(1, "客户自助"),
    ADMIN(2, "客服代建"),
    ;

    private final Integer source;
    private final String name;

    public static String nameOf(Integer source) {
        for (CustomerServiceSourceEnum value : values()) {
            if (value.getSource().equals(source)) {
                return value.getName();
            }
        }
        return "";
    }

}
