package cn.iocoder.yudao.module.trade.controller.admin.customerservice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairOrderConfirmOfflineReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairOrderPageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairOrderRefundReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairOrderRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderPayStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairStatusEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairOrderService;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 管理后台 - 维修订单（021 / US73）
 */
@Tag(name = "管理后台 - 维修订单")
@RestController
@RequestMapping("/trade/customer-service/repair-order")
@Validated
public class AdminCustomerServiceRepairOrderController {

    @Resource
    private CustomerServiceRepairOrderService customerServiceRepairOrderService;
    @Resource
    private CustomerServiceRepairRequestService customerServiceRepairRequestService;
    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private AdminUserApi adminUserApi;

    @GetMapping("/page")
    @Operation(summary = "维修订单分页")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:query')")
    public CommonResult<PageResult<AdminCustomerServiceRepairOrderRespVO>> page(
            @Valid AdminCustomerServiceRepairOrderPageReqVO reqVO) {
        PageResult<CustomerServiceRepairOrderDO> page = customerServiceRepairOrderService.getPage(
                reqVO.getId(), reqVO.getMemberId(), reqVO.getPayStatus(), reqVO.getRefundStatus(),
                reqVO.getCreateTime(), reqVO);
        List<Long> memberIds = page.getList().stream().map(CustomerServiceRepairOrderDO::getMemberId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> requestIds = page.getList().stream().map(CustomerServiceRepairOrderDO::getRepairRequestId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, MemberUserRespDTO> memberMap = memberIds.isEmpty() ? Map.of()
                : memberUserApi.getUserList(memberIds).getCheckedData().stream()
                .collect(Collectors.toMap(MemberUserRespDTO::getId, user -> user, (a, b) -> a));
        Map<Long, CustomerServiceRepairRequestDO> requestMap = requestIds.isEmpty() ? Map.of()
                : customerServiceRepairRequestService.getRepairs(requestIds).stream()
                .collect(Collectors.toMap(CustomerServiceRepairRequestDO::getId, request -> request, (a, b) -> a));
        List<Long> completeOperatorIds = page.getList().stream()
                .map(CustomerServiceRepairOrderDO::getCompleteOperatorId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> completeOperatorMap = completeOperatorIds.isEmpty() ? Map.of()
                : adminUserApi.getUserList(completeOperatorIds).getCheckedData().stream()
                .collect(Collectors.toMap(AdminUserRespDTO::getId,
                        user -> user.getNickname() == null ? "" : user.getNickname(), (a, b) -> a));
        List<AdminCustomerServiceRepairOrderRespVO> list = page.getList().stream()
                .map(order -> toRespVO(order, memberMap.get(order.getMemberId()),
                        requestMap.get(order.getRepairRequestId()),
                        completeOperatorMap.get(order.getCompleteOperatorId())))
                .toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @PutMapping("/complete")
    @Operation(summary = "完成维修")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<Boolean> complete(@RequestParam("id") Long id) {
        customerServiceRepairOrderService.complete(id, getLoginUserId());
        return success(true);
    }

    @PutMapping("/refund")
    @Operation(summary = "维修订单全额退款")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:refund')")
    public CommonResult<Boolean> refund(@Valid @RequestBody AdminCustomerServiceRepairOrderRefundReqVO reqVO) {
        customerServiceRepairOrderService.refund(reqVO.getId(), getLoginUserId(), getClientIP(), reqVO.getReason());
        return success(true);
    }

    @PostMapping("/confirm-offline")
    @Operation(summary = "确认对公转账收款")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<Boolean> confirmOffline(
            @Valid @RequestBody AdminCustomerServiceRepairOrderConfirmOfflineReqVO reqVO) {
        customerServiceRepairOrderService.confirmOffline(reqVO.getId(), getLoginUserId(),
                reqVO.getBankSerialNo(), reqVO.getPayerName(), reqVO.getPaidTime(), reqVO.getRemark());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "维修订单详情")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('trade:customer-service:query')")
    public CommonResult<AdminCustomerServiceRepairOrderRespVO> get(@RequestParam("id") Long id) {
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderService.getById(id);
        if (order == null) {
            return success(null);
        }
        MemberUserRespDTO member = order.getMemberId() == null ? null
                : memberUserApi.getUser(order.getMemberId()).getCheckedData();
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestService
                .getRepairs(List.of(order.getRepairRequestId())).stream().findFirst().orElse(null);
        AdminUserRespDTO completeOperator = order.getCompleteOperatorId() == null ? null
                : adminUserApi.getUser(order.getCompleteOperatorId()).getCheckedData();
        return success(toRespVO(order, member, request,
                completeOperator == null ? null : completeOperator.getNickname()));
    }

    private AdminCustomerServiceRepairOrderRespVO toRespVO(CustomerServiceRepairOrderDO order,
                                                           MemberUserRespDTO member,
                                                           CustomerServiceRepairRequestDO request,
                                                           String completeOperatorName) {
        AdminCustomerServiceRepairOrderRespVO vo = new AdminCustomerServiceRepairOrderRespVO();
        vo.setId(order.getId());
        vo.setRepairRequestId(order.getRepairRequestId());
        vo.setMemberId(order.getMemberId());
        vo.setMemberNickname(member == null ? null : member.getNickname());
        vo.setMemberMobile(member == null ? null : member.getMobile());
        vo.setDeviceModel(request == null ? null : request.getDeviceModel());
        vo.setRepairFee(order.getRepairFee());
        vo.setReturnFreight(order.getReturnFreight());
        vo.setPayableAmount(order.getPayableAmount());
        vo.setFeeRemark(order.getFeeRemark());
        vo.setPayStatus(order.getPayStatus());
        vo.setPayStatusName(CustomerServiceRepairOrderPayStatusEnum.nameOf(order.getPayStatus()));
        vo.setPayChannelCode(order.getPayChannelCode());
        vo.setPayOrderId(order.getPayOrderId());
        vo.setPayTime(order.getPayTime());
        vo.setOfflineConfirmUserId(order.getOfflineConfirmUserId());
        vo.setOfflineConfirmTime(order.getOfflineConfirmTime());
        vo.setRefundStatus(order.getRefundStatus());
        vo.setRefundStatusName(CustomerServiceRepairOrderRefundStatusEnum.nameOf(order.getRefundStatus()));
        vo.setPayRefundId(order.getPayRefundId());
        vo.setRefundPrice(order.getRefundPrice());
        vo.setRefundReason(order.getRefundReason());
        vo.setRefundTime(order.getRefundTime());
        vo.setRefundOperatorId(order.getRefundOperatorId());
        vo.setCompleteTime(order.getCompleteTime());
        vo.setCompleteOperatorId(order.getCompleteOperatorId());
        vo.setCompleteOperatorName(completeOperatorName);
        if (request != null) {
            vo.setRepairStatus(request.getStatus());
            vo.setRepairStatusName(CustomerServiceRepairStatusEnum.nameOf(request.getStatus()));
        }
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

}
