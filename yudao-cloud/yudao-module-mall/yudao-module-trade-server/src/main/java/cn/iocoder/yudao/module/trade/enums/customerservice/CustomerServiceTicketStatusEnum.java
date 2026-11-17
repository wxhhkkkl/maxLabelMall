package cn.iocoder.yudao.module.trade.enums.customerservice;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户服务投诉咨询状态（021）
 */
@Getter
@AllArgsConstructor
public enum CustomerServiceTicketStatusEnum {

    PENDING(0, "待处理"),
    HANDLED(10, "已处理"),
    ;

    private final Integer status;
    private final String name;

    public static String nameOf(Integer status) {
        for (CustomerServiceTicketStatusEnum value : values()) {
            if (value.getStatus().equals(status)) {
                return value.getName();
            }
        }
        return "";
    }

}
