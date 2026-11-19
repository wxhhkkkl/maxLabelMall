package cn.iocoder.yudao.module.trade.controller.admin.customerservice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairOrderPageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairOrderRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderPayStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairStatusEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairOrderService;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairRequestService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * 管理端维修订单分页（021 / 045）
 *
 * 回归：未完成维修的订单 `complete_operator_id` 为 NULL，早期实现用 `Map.of().get(null)`
 * 直接抛 NPE，导致整页 500「系统异常」。
 */
public class AdminCustomerServiceRepairOrderPageTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AdminCustomerServiceRepairOrderController controller;

    @Mock
    private CustomerServiceRepairOrderService customerServiceRepairOrderService;
    @Mock
    private CustomerServiceRepairRequestService customerServiceRepairRequestService;
    @Mock
    private MemberUserApi memberUserApi;
    @Mock
    private AdminUserApi adminUserApi;

    @Test
    public void testPage_completeOperatorIdNull() {
        CustomerServiceRepairOrderDO order = CustomerServiceRepairOrderDO.builder()
                .id(1L).repairRequestId(2L).memberId(3L)
                .repairFee(100).returnFreight(0).payableAmount(100)
                .payStatus(CustomerServiceRepairOrderPayStatusEnum.PAID.getStatus())
                .refundStatus(CustomerServiceRepairOrderRefundStatusEnum.REFUNDED.getStatus())
                .completeOperatorId(null)
                .build();
        when(customerServiceRepairOrderService.getPage(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(order), 1L));
        when(customerServiceRepairRequestService.getRepairs(anyCollection()))
                .thenReturn(List.of(CustomerServiceRepairRequestDO.builder().id(2L)
                        .status(CustomerServiceRepairStatusEnum.REPAIRING.getStatus())
                        .deviceModel("gank123").build()));
        MemberUserRespDTO member = new MemberUserRespDTO();
        member.setId(3L);
        member.setNickname("测试会员");
        when(memberUserApi.getUserList(anyCollection())).thenReturn(CommonResult.success(List.of(member)));

        CommonResult<PageResult<AdminCustomerServiceRepairOrderRespVO>> result =
                controller.page(new AdminCustomerServiceRepairOrderPageReqVO());

        assertEquals(0, result.getCode());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getList().size());
        AdminCustomerServiceRepairOrderRespVO vo = result.getData().getList().get(0);
        assertEquals("测试会员", vo.getMemberNickname());
        assertNull(vo.getCompleteOperatorId());
        assertNull(vo.getCompleteOperatorName());
    }

}
