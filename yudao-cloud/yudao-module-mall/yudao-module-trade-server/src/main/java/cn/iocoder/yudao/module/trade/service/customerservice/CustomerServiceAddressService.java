package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceAddressDO;

/**
 * 客户服务 - 回收邮寄地址 Service（021）
 *
 * 每租户一条；US73 读取、US74 维护共用这一份实现。
 */
public interface CustomerServiceAddressService {

    /** 当前租户的回收地址；未配置返回 null */
    CustomerServiceAddressDO getAddress();

    /** 保存回收地址（不存在则创建，存在则更新） */
    void updateAddress(CustomerServiceAddressDO address);

}
