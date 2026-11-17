package cn.iocoder.yudao.module.trade.enums.customerservice;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户服务投诉咨询类型（021）
 */
@Getter
@AllArgsConstructor
public enum CustomerServiceTicketTypeEnum {

    COMPLAINT(1, "投诉"),
    CONSULT(2, "咨询"),
    ;

    private final Integer type;
    private final String name;

    public static String nameOf(Integer type) {
        for (CustomerServiceTicketTypeEnum value : values()) {
            if (value.getType().equals(type)) {
                return value.getName();
            }
        }
        return "";
    }

}
