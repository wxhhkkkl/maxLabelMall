package cn.iocoder.yudao.module.trade.enums.customerservice;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 维修申请状态（021）
 *
 * 0 待联系 → 10 已联系 → 20 已开单 → 30 已寄出 → 40 维修中 → 50 已完成
 * 60 已取消（客服触发）
 */
@Getter
@AllArgsConstructor
public enum CustomerServiceRepairStatusEnum {

    PENDING_CONTACT(0, "待联系"),
    CONTACTED(10, "已联系"),
    ORDERED(20, "已开单"),
    SHIPPED(30, "已寄出"),
    REPAIRING(40, "维修中"),
    COMPLETED(50, "已完成"),
    CANCELED(60, "已取消"),
    ;

    private final Integer status;
    private final String name;

    public static String nameOf(Integer status) {
        for (CustomerServiceRepairStatusEnum value : values()) {
            if (value.getStatus().equals(status)) {
                return value.getName();
            }
        }
        return "";
    }

    public static boolean isCanceled(Integer status) {
        return CANCELED.getStatus().equals(status);
    }

    public static boolean isCompleted(Integer status) {
        return COMPLETED.getStatus().equals(status);
    }

}
