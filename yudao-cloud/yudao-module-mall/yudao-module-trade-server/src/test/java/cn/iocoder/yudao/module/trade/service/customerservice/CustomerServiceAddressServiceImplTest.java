package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceAddressDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceAddressMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 回收邮寄地址（021 / FR-577）
 */
@Import(CustomerServiceAddressServiceImpl.class)
public class CustomerServiceAddressServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CustomerServiceAddressService customerServiceAddressService;
    @Resource
    private CustomerServiceAddressMapper customerServiceAddressMapper;

    private CustomerServiceAddressDO address(String receiver, String detail) {
        return CustomerServiceAddressDO.builder()
                .receiverName(receiver).receiverMobile("010-88613986")
                .areaName("北京市 北京市 海淀区").detailAddress(detail)
                .shipRemark("请注明维修单号").build();
    }

    @Test
    public void testGetAddress_whenNotConfigured_returnsNull() {
        assertNull(customerServiceAddressService.getAddress());
    }

    @Test
    public void testUpdateAddress_createThenUpdate_keepsSingleRow() {
        customerServiceAddressService.updateAddress(address("李四", "xx 路 1 号"));
        CustomerServiceAddressDO created = customerServiceAddressService.getAddress();
        assertEquals("李四", created.getReceiverName());
        assertEquals(1, customerServiceAddressMapper.selectList().size());

        customerServiceAddressService.updateAddress(address("王五", "yy 路 2 号"));
        CustomerServiceAddressDO updated = customerServiceAddressService.getAddress();
        assertEquals("王五", updated.getReceiverName());
        assertEquals("yy 路 2 号", updated.getDetailAddress());
        assertEquals(1, customerServiceAddressMapper.selectList().size(), "每租户只保留一条");
    }

}
