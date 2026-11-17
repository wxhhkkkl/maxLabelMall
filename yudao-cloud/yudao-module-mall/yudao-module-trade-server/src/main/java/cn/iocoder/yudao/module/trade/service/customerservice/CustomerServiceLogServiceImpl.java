package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceLogDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客户服务流转记录 Service 实现（021）
 */
@Service
public class CustomerServiceLogServiceImpl implements CustomerServiceLogService {

    @Resource
    private CustomerServiceLogMapper customerServiceLogMapper;

    @Override
    public void addLog(Integer targetType, Long targetId, String action,
                       Integer fromStatus, Integer toStatus, String remark,
                       Integer operatorType, Long operatorId) {
        customerServiceLogMapper.insert(CustomerServiceLogDO.builder()
                .targetType(targetType).targetId(targetId).action(action)
                .fromStatus(fromStatus).toStatus(toStatus).remark(remark)
                .operatorType(operatorType).operatorId(operatorId)
                .build());
    }

    @Override
    public List<CustomerServiceLogDO> getLogs(Integer targetType, Long targetId) {
        return customerServiceLogMapper.selectListByTarget(targetType, targetId);
    }

}
