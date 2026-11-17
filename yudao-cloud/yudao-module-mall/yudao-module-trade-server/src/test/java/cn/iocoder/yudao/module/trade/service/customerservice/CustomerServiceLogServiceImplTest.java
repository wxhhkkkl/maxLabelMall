package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceLogDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceLogMapper;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceActionConstants;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTargetTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 客户服务流转记录（021 / FR-581）
 */
@Import(CustomerServiceLogServiceImpl.class)
public class CustomerServiceLogServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CustomerServiceLogService customerServiceLogService;
    @Resource
    private CustomerServiceLogMapper customerServiceLogMapper;

    @Test
    public void testAddLog_andQuery() {
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), 601L,
                CustomerServiceActionConstants.CONTACT, 0, 10, "已电话联系",
                2, 1L);

        List<CustomerServiceLogDO> logs = customerServiceLogMapper.selectListByTarget(
                CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), 601L);
        assertEquals(1, logs.size());
        CustomerServiceLogDO log = logs.get(0);
        assertNotNull(log.getId());
        assertEquals(CustomerServiceActionConstants.CONTACT, log.getAction());
        assertEquals(0, log.getFromStatus());
        assertEquals(10, log.getToStatus());
        assertEquals("已电话联系", log.getRemark());
    }

    @Test
    public void testAddLog_appendsOnly() {
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.TICKET.getType(), 501L,
                CustomerServiceActionConstants.CREATE, null, 0, "创建", 1, 88L);
        customerServiceLogService.addLog(CustomerServiceTargetTypeEnum.TICKET.getType(), 501L,
                CustomerServiceActionConstants.HANDLE, 0, 10, "已处理", 2, 1L);

        List<CustomerServiceLogDO> logs = customerServiceLogMapper.selectListByTarget(
                CustomerServiceTargetTypeEnum.TICKET.getType(), 501L);
        assertEquals(2, logs.size(), "追加两条，不覆盖第一条");
        assertEquals(CustomerServiceActionConstants.CREATE, logs.get(0).getAction());
        assertEquals(CustomerServiceActionConstants.HANDLE, logs.get(1).getAction());
    }

}
