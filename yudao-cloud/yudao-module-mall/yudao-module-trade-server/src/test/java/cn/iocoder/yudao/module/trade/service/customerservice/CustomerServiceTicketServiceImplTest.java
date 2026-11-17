package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceTicketDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceLogMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.customerservice.CustomerServiceTicketMapper;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceSourceEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTicketStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTicketTypeEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceTicketCreateReqBO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 投诉咨询（021 / US70）
 */
@Import({CustomerServiceTicketServiceImpl.class, CustomerServiceLogServiceImpl.class,
        CustomerServiceFileValidator.class})
public class CustomerServiceTicketServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CustomerServiceTicketService customerServiceTicketService;
    @Resource
    private CustomerServiceTicketMapper customerServiceTicketMapper;
    @Resource
    private CustomerServiceLogMapper customerServiceLogMapper;

    private CustomerServiceTicketCreateReqBO req(Long memberId) {
        CustomerServiceTicketCreateReqBO reqBO = new CustomerServiceTicketCreateReqBO();
        reqBO.setMemberId(memberId);
        reqBO.setType(CustomerServiceTicketTypeEnum.COMPLAINT.getType());
        reqBO.setContent("打印时标签总是偏移");
        reqBO.setContactMobile("13800008888");
        reqBO.setSource(CustomerServiceSourceEnum.MEMBER.getSource());
        reqBO.setCreatorId(memberId);
        return reqBO;
    }

    @Test
    public void testCreate_valid() {
        Long id = customerServiceTicketService.createTicket(req(88L));

        CustomerServiceTicketDO ticket = customerServiceTicketMapper.selectById(id);
        assertNotNull(ticket);
        assertEquals(88L, ticket.getMemberId());
        assertEquals(CustomerServiceTicketStatusEnum.PENDING.getStatus(), ticket.getStatus());
        assertEquals(CustomerServiceSourceEnum.MEMBER.getSource(), ticket.getSource());
        assertEquals(1, customerServiceLogMapper.selectListByTarget(1, id).size());
    }

    @Test
    public void testCreate_invalidContentOrMobile_rejected() {
        CustomerServiceTicketCreateReqBO blankContent = req(88L);
        blankContent.setContent(" ");
        assertThrows(ServiceException.class, () -> customerServiceTicketService.createTicket(blankContent));

        CustomerServiceTicketCreateReqBO badMobile = req(88L);
        badMobile.setContactMobile("12345");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceTicketService.createTicket(badMobile));
        assertEquals(CUSTOMER_SERVICE_TICKET_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void testCreate_sameContentWithin60s_deduped() {
        Long first = customerServiceTicketService.createTicket(req(88L));
        Long second = customerServiceTicketService.createTicket(req(88L));

        assertEquals(first, second);
        assertEquals(1, customerServiceTicketMapper.selectList().size());
    }

    @Test
    public void testMyTicket_isolation() {
        Long id = customerServiceTicketService.createTicket(req(88L));

        assertThrows(ServiceException.class, () -> customerServiceTicketService.getMyTicket(99L, id));
        PageResult<CustomerServiceTicketDO> page = customerServiceTicketService.getMyTicketPage(
                99L, new PageParam());
        assertEquals(0, page.getTotal());
    }

    @Test
    public void testHandle_setsRemarkAndStatus_logged() {
        Long id = customerServiceTicketService.createTicket(req(88L));

        customerServiceTicketService.handleTicket(id, "已电话回访并指导恢复", 1L);

        CustomerServiceTicketDO handled = customerServiceTicketMapper.selectById(id);
        assertEquals(CustomerServiceTicketStatusEnum.HANDLED.getStatus(), handled.getStatus());
        assertEquals("已电话回访并指导恢复", handled.getHandleRemark());
        assertEquals(1L, handled.getHandlerId());
        assertNotNull(handled.getHandleTime());
        assertEquals(2, customerServiceLogMapper.selectListByTarget(1, id).size());
    }

    @Test
    public void testHandle_invalidOrRepeated_rejected() {
        Long id = customerServiceTicketService.createTicket(req(88L));
        assertThrows(ServiceException.class, () -> customerServiceTicketService.handleTicket(id, " ", 1L));

        customerServiceTicketService.handleTicket(id, "已处理", 1L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceTicketService.handleTicket(id, "再处理", 1L));
        assertEquals(CUSTOMER_SERVICE_TICKET_HANDLED.getCode(), ex.getCode());
    }

    @Test
    public void testCreate_adminSource_recorded() {
        CustomerServiceTicketCreateReqBO reqBO = req(88L);
        reqBO.setSource(CustomerServiceSourceEnum.ADMIN.getSource());
        reqBO.setCreatorId(1L);
        Long id = customerServiceTicketService.createTicket(reqBO);

        CustomerServiceTicketDO ticket = customerServiceTicketMapper.selectById(id);
        assertEquals(CustomerServiceSourceEnum.ADMIN.getSource(), ticket.getSource());
        assertEquals(1L, ticket.getCreatorId());
    }

    @Test
    public void testCreate_withImages_serialized() {
        CustomerServiceTicketCreateReqBO reqBO = req(88L);
        reqBO.setImages(List.of("https://img.yuwangchenfa.com/20261117/a.jpg"));
        Long id = customerServiceTicketService.createTicket(reqBO);

        assertNotNull(customerServiceTicketMapper.selectById(id).getImages());
    }

    @Test
    public void testCreate_limit10Pending_untilHandled() {
        Long firstId = null;
        for (int i = 0; i < 10; i++) {
            CustomerServiceTicketCreateReqBO reqBO = req(88L);
            reqBO.setContent("第 " + i + " 条咨询");
            Long id = customerServiceTicketService.createTicket(reqBO);
            if (firstId == null) firstId = id;
        }
        CustomerServiceTicketCreateReqBO eleventh = req(88L);
        eleventh.setContent("第 11 条咨询");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> customerServiceTicketService.createTicket(eleventh));
        assertEquals(CUSTOMER_SERVICE_TICKET_LIMIT.getCode(), ex.getCode());

        // 处理一条后可以继续提交
        customerServiceTicketMapper.updateById(new CustomerServiceTicketDO().setId(firstId)
                .setStatus(CustomerServiceTicketStatusEnum.HANDLED.getStatus())
                .setHandleRemark("已处理"));
        assertNotNull(customerServiceTicketService.createTicket(eleventh));
    }

}
