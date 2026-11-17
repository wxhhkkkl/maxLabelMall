package cn.iocoder.yudao.module.trade.enums.customerservice;

/**
 * 客户服务的操作日志文案（021）
 */
public interface CustomerServiceLogRecordConstants {

    String CUSTOMER_SERVICE_TYPE = "客户服务";

    String TICKET_HANDLE_SUB_TYPE = "处理投诉咨询";
    String TICKET_HANDLE_SUCCESS = "处理了工单【{{#id}}】";

    String REPAIR_CONTACT_SUB_TYPE = "记录维修联系";
    String REPAIR_CONTACT_SUCCESS = "记录了维修申请【{{#id}}】的电话联系结果";

    String REPAIR_ORDER_CREATE_SUB_TYPE = "生成维修订单";
    String REPAIR_ORDER_CREATE_SUCCESS = "为维修申请【{{#repairRequestId}}】生成了维修订单";

    String REPAIR_ORDER_CONFIRM_OFFLINE_SUB_TYPE = "确认维修对公收款";
    String REPAIR_ORDER_CONFIRM_OFFLINE_SUCCESS = "确认了维修订单【{{#id}}】的对公转账收款";

    String REPAIR_STATUS_SUB_TYPE = "修改维修状态";
    String REPAIR_STATUS_SUCCESS = "将维修申请【{{#id}}】的状态改为 {{#status}}";

    String REPAIR_CANCEL_SUB_TYPE = "取消维修申请";
    String REPAIR_CANCEL_SUCCESS = "取消了维修申请【{{#id}}】";

    String ADDRESS_UPDATE_SUB_TYPE = "修改回收地址";
    String ADDRESS_UPDATE_SUCCESS = "修改了客户服务的回收邮寄地址";

}
