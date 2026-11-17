package cn.iocoder.yudao.module.trade.enums.customerservice;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 维修订单退款状态（021 补充）
 */
@Getter
@AllArgsConstructor
public enum CustomerServiceRepairOrderRefundStatusEnum {

    NOT_REFUNDED(0, "未退款"),
    REFUNDING(10, "退款中"),
    REFUNDED(20, "已退款"),
    REFUND_FAILED(30, "退款失败");

    private final Integer status;
    private final String name;

    public static String nameOf(Integer status) {
        return Arrays.stream(values()).filter(item -> item.getStatus().equals(status))
                .map(CustomerServiceRepairOrderRefundStatusEnum::getName).findFirst().orElse("");
    }

}
