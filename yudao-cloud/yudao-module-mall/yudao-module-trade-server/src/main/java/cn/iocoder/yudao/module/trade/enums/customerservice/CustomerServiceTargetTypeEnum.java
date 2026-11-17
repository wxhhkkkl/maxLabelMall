package cn.iocoder.yudao.module.trade.enums.customerservice;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户服务流转记录的目标类型（021）
 */
@Getter
@AllArgsConstructor
public enum CustomerServiceTargetTypeEnum {

    TICKET(1, "投诉咨询"),
    REPAIR_REQUEST(2, "维修申请"),
    REPAIR_ORDER(3, "维修订单"),
    ;

    private final Integer type;
    private final String name;

}
