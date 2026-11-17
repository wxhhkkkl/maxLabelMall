package cn.iocoder.yudao.module.trade.enums.customerservice;

/**
 * 客户服务流转记录的动作（021）
 *
 * 与 `trade_customer_service_log.action` 一一对应，只增不改。
 */
public interface CustomerServiceActionConstants {

    String CREATE = "CREATE";
    String HANDLE = "HANDLE";
    String CONTACT = "CONTACT";
    String ORDER_CREATE = "ORDER_CREATE";
    String PAY_ONLINE = "PAY_ONLINE";
    String PAY_OFFLINE = "PAY_OFFLINE";
    String PAY_CONFIRM = "PAY_CONFIRM";
    String SHIP = "SHIP";
    String REPAIRING = "REPAIRING";
    String COMPLETE = "COMPLETE";
    String CANCEL = "CANCEL";
    String ADDRESS_UPDATE = "ADDRESS_UPDATE";
    String REFUND = "REFUND";
    String REFUND_SUCCESS = "REFUND_SUCCESS";
    String REFUND_FAIL = "REFUND_FAIL";

}
