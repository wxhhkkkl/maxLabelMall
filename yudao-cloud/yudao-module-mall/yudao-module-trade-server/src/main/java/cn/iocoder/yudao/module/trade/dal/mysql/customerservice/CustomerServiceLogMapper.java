package cn.iocoder.yudao.module.trade.dal.mysql.customerservice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CustomerServiceLogMapper extends BaseMapperX<CustomerServiceLogDO> {

    default List<CustomerServiceLogDO> selectListByTarget(Integer targetType, Long targetId) {
        return selectList(new LambdaQueryWrapperX<CustomerServiceLogDO>()
                .eq(CustomerServiceLogDO::getTargetType, targetType)
                .eq(CustomerServiceLogDO::getTargetId, targetId)
                .orderByAsc(CustomerServiceLogDO::getId));
    }

}
