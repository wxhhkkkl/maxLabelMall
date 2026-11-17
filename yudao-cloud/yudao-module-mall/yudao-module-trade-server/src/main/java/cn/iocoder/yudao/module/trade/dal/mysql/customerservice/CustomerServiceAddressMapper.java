package cn.iocoder.yudao.module.trade.dal.mysql.customerservice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceAddressDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerServiceAddressMapper extends BaseMapperX<CustomerServiceAddressDO> {

    default CustomerServiceAddressDO selectCurrent() {
        return selectOne(new LambdaQueryWrapperX<CustomerServiceAddressDO>()
                .orderByDesc(CustomerServiceAddressDO::getId)
                .last("LIMIT 1"));
    }

}
