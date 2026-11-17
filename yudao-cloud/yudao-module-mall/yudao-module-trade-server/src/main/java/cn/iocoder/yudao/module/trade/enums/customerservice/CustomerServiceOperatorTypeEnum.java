package cn.iocoder.yudao.module.trade.enums.customerservice;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户服务流转记录的操作人类型（021）
 */
@Getter
@AllArgsConstructor
public enum CustomerServiceOperatorTypeEnum {

    MEMBER(1, "会员"),
    ADMIN(2, "管理员"),
    SYSTEM(3, "系统"),
    ;

    private final Integer type;
    private final String name;

}
