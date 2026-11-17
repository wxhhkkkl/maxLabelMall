package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceAddressDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceAddressMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户服务 - 回收邮寄地址 Service 实现（021）
 */
@Service
public class CustomerServiceAddressServiceImpl implements CustomerServiceAddressService {

    @Resource
    private CustomerServiceAddressMapper customerServiceAddressMapper;

    @Override
    public CustomerServiceAddressDO getAddress() {
        return customerServiceAddressMapper.selectCurrent();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(CustomerServiceAddressDO address) {
        CustomerServiceAddressDO current = customerServiceAddressMapper.selectCurrent();
        if (current == null) {
            customerServiceAddressMapper.insert(address);
            return;
        }
        address.setId(current.getId());
        customerServiceAddressMapper.updateById(address);
    }

}
