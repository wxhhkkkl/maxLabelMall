package cn.iocoder.yudao.module.trade.enums.customerservice;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 维修订单支付状态（021）
 */
@Getter
@AllArgsConstructor
public enum CustomerServiceRepairOrderPayStatusEnum {

    UNPAID(0, "待支付"),
    WAITING_CONFIRM(10, "待确认收款"),
    PAID(20, "已支付"),
    NO_PAY(30, "无需支付"),
    CANCELED(40, "已取消"),
    ;

    private final Integer status;
    private final String name;

    public static String nameOf(Integer status) {
        for (CustomerServiceRepairOrderPayStatusEnum value : values()) {
            if (value.getStatus().equals(status)) {
                return value.getName();
            }
        }
        return "";
    }

    public static boolean isPaidOrNoPay(Integer status) {
        return PAID.getStatus().equals(status) || NO_PAY.getStatus().equals(status);
    }

}
