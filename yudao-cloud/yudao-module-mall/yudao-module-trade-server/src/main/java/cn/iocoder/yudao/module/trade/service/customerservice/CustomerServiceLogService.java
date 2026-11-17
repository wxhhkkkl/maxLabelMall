package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceLogDO;

import java.util.List;

/**
 * 客户服务流转记录 Service（021）
 *
 * 只追加、不修改；客户可见的时间线与后台 @LogRecord 各司其职。
 */
public interface CustomerServiceLogService {

    void addLog(Integer targetType, Long targetId, String action,
                Integer fromStatus, Integer toStatus, String remark,
                Integer operatorType, Long operatorId);

    List<CustomerServiceLogDO> getLogs(Integer targetType, Long targetId);

}
