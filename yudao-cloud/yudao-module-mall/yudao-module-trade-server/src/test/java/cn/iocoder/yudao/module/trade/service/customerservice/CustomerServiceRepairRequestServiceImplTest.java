package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceRepairRequestMapper;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceSourceEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceRepairCreateReqBO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 维修申请（021 / US71）
 */
@Import({CustomerServiceRepairRequestServiceImpl.class, CustomerServiceLogServiceImpl.class,
        CustomerServiceFileValidator.class})
public class CustomerServiceRepairRequestServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CustomerServiceRepairRequestService customerServiceRepairRequestService;
    @Resource
    private CustomerServiceRepairRequestMapper customerServiceRepairRequestMapper;

    private CustomerServiceRepairCreateReqBO req(Long memberId) {
        CustomerServiceRepairCreateReqBO reqBO = new CustomerServiceRepairCreateReqBO();
        reqBO.setMemberId(memberId);
        reqBO.setDeviceModel("Zebra ZT411");
        reqBO.setFaultDesc("开机后走纸不停，伴随异响");
        reqBO.setContactMobile("13800008888");
        reqBO.setReceiverName("张三");
        reqBO.setReceiverMobile("13800008888");
        reqBO.setReceiverArea("江苏省 南京市 鼓楼区");
        reqBO.setReceiverDetail("xx 路 1 号");
        reqBO.setSource(CustomerServiceSourceEnum.MEMBER.getSource());
        reqBO.setCreatorId(memberId);
        return reqBO;
    }

    @Test
    public void testCreate_valid() {
        Long id = customerServiceRepairRequestService.createRepair(req(88L));

        CustomerServiceRepairRequestDO request = customerServiceRepairRequestMapper.selectById(id);
        assertNotNull(request);
        assertEquals(88L, request.getMemberId());
        assertEquals(CustomerServiceRepairStatusEnum.PENDING_CONTACT.getStatus(), request.getStatus());
        assertEquals(CustomerServiceSourceEnum.MEMBER.getSource(), request.getSource());
        assertEquals("Zebra ZT411", request.getDeviceModel());
    }

    @Test
    public void testCreate_missingRequired_rejected() {
        CustomerServiceRepairCreateReqBO noDevice = req(88L);
        noDevice.setDeviceModel(" ");
        assertThrows(ServiceException.class, () -> customerServiceRepairRequestService.createRepair(noDevice));

        CustomerServiceRepairCreateReqBO noReceiver = req(88L);
        noReceiver.setReceiverDetail("");
        assertThrows(ServiceException.class, () -> customerServiceRepairRequestService.createRepair(noReceiver));
    }

    @Test
    public void testMyRepair_isolation() {
        Long id = customerServiceRepairRequestService.createRepair(req(88L));

        assertThrows(ServiceException.class, () -> customerServiceRepairRequestService.getMyRepair(99L, id));
        PageResult<CustomerServiceRepairRequestDO> page = customerServiceRepairRequestService.getMyRepairPage(
                99L, new PageParam());
        assertEquals(0, page.getTotal());
    }

    @Test
    public void testUpdateStatus_allowedTransitionsOnly() {
        Long id = customerServiceRepairRequestService.createRepair(req(88L));

        // 0 → 40 非法
        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairRequestService.updateStatus(id,
                        CustomerServiceRepairStatusEnum.REPAIRING.getStatus(), "收到设备", 1L));
        assertEquals(CUSTOMER_SERVICE_REPAIR_STATUS_INVALID.getCode(), ex.getCode());

        // 先模拟已寄出（20→30 由 US73 的寄出逻辑完成）
        CustomerServiceRepairRequestDO shipped = new CustomerServiceRepairRequestDO();
        shipped.setId(id);
        shipped.setStatus(CustomerServiceRepairStatusEnum.SHIPPED.getStatus());
        customerServiceRepairRequestMapper.updateById(shipped);

        customerServiceRepairRequestService.updateStatus(id,
                CustomerServiceRepairStatusEnum.REPAIRING.getStatus(), "收到设备", 1L);
        assertEquals(CustomerServiceRepairStatusEnum.REPAIRING.getStatus(),
                customerServiceRepairRequestMapper.selectById(id).getStatus());

        customerServiceRepairRequestService.updateStatus(id,
                CustomerServiceRepairStatusEnum.COMPLETED.getStatus(), "维修完成", 1L);
        assertEquals(CustomerServiceRepairStatusEnum.COMPLETED.getStatus(),
                customerServiceRepairRequestMapper.selectById(id).getStatus());
    }

    @Test
    public void testCreate_adminSource_recorded() {
        CustomerServiceRepairCreateReqBO reqBO = req(88L);
        reqBO.setSource(CustomerServiceSourceEnum.ADMIN.getSource());
        reqBO.setCreatorId(1L);
        Long id = customerServiceRepairRequestService.createRepair(reqBO);

        CustomerServiceRepairRequestDO request = customerServiceRepairRequestMapper.selectById(id);
        assertEquals(CustomerServiceSourceEnum.ADMIN.getSource(), request.getSource());
        assertEquals(1L, request.getCreatorId());
    }

    @Test
    public void testCreate_limit10Active_untilCompleted() {
        Long firstId = null;
        for (int i = 0; i < 10; i++) {
            CustomerServiceRepairCreateReqBO reqBO = req(88L);
            reqBO.setDeviceModel("Zebra ZT41" + i);
            Long id = customerServiceRepairRequestService.createRepair(reqBO);
            if (firstId == null) firstId = id;
        }
        CustomerServiceRepairCreateReqBO eleventh = req(88L);
        eleventh.setDeviceModel("Zebra ZT411-11");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceRepairRequestService.createRepair(eleventh));
        assertEquals(CUSTOMER_SERVICE_REPAIR_LIMIT.getCode(), ex.getCode());

        // 完成或取消一台后可以继续提交
        customerServiceRepairRequestMapper.updateById(new CustomerServiceRepairRequestDO().setId(firstId)
                .setStatus(CustomerServiceRepairStatusEnum.COMPLETED.getStatus()));
        assertNotNull(customerServiceRepairRequestService.createRepair(eleventh));
    }

}
