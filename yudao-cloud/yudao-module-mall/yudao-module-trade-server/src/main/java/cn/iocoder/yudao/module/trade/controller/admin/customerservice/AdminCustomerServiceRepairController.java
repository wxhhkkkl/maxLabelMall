package cn.iocoder.yudao.module.trade.controller.admin.customerservice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceAddressReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceLogRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairCancelReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairContactReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairOrderCreateRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairPageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceRepairStatusReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceAddressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceLogDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderPayStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceSourceEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTargetTypeEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceAddressService;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceLogService;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceMemberSupport;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairOrderService;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairRequestService;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceRepairCreateReqBO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 管理后台 - 客户服务在线维修（021 / US71）
 */
@Tag(name = "管理后台 - 客户服务在线维修")
@RestController
@RequestMapping("/trade/customer-service/repair")
@Validated
public class AdminCustomerServiceRepairController {

    @Resource
    private CustomerServiceRepairRequestService customerServiceRepairRequestService;
    @Resource
    private CustomerServiceRepairOrderService customerServiceRepairOrderService;
    @Resource
    private CustomerServiceLogService customerServiceLogService;
    @Resource
    private CustomerServiceAddressService customerServiceAddressService;
    @Resource
    private CustomerServiceMemberSupport customerServiceMemberSupport;
    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private AdminUserApi adminUserApi;

    @GetMapping("/page")
    @Operation(summary = "维修申请分页")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:query')")
    public CommonResult<PageResult<AdminCustomerServiceRepairRespVO>> page(
            @Valid AdminCustomerServiceRepairPageReqVO reqVO) {
        Collection<Long> memberIds = null;
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            List<MemberUserRespDTO> members = customerServiceMemberSupport.search(reqVO.getKeyword());
            memberIds = CollUtil.isEmpty(members) ? null : members.stream()
                    .map(MemberUserRespDTO::getId).collect(Collectors.toSet());
        }
        PageResult<CustomerServiceRepairRequestDO> page = customerServiceRepairRequestService.getRepairPage(
                reqVO.getStatus(), reqVO.getSource(), memberIds, reqVO.getKeyword(),
                reqVO.getCreateTime(), reqVO);
        if (CollUtil.isEmpty(page.getList())) {
            return success(PageResult.empty());
        }
        Map<Long, MemberUserRespDTO> memberMap = getMemberMap(page.getList().stream()
                .map(CustomerServiceRepairRequestDO::getMemberId).collect(Collectors.toSet()));
        Map<Long, CustomerServiceRepairOrderDO> orderMap = customerServiceRepairOrderService
                .getListByRepairRequestIds(page.getList().stream()
                        .map(CustomerServiceRepairRequestDO::getId).toList())
                .stream().collect(Collectors.toMap(
                        CustomerServiceRepairOrderDO::getRepairRequestId, order -> order, (a, b) -> a));
        // 开单人：客服代建的取管理员昵称；客户自助的取会员昵称
        List<Long> adminCreatorIds = page.getList().stream()
                .filter(request -> Objects.equals(CustomerServiceSourceEnum.ADMIN.getSource(), request.getSource()))
                .map(CustomerServiceRepairRequestDO::getCreatorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> adminCreatorMap = adminCreatorIds.isEmpty() ? Map.of()
                : adminUserApi.getUserList(adminCreatorIds).getCheckedData().stream()
                .collect(Collectors.toMap(AdminUserRespDTO::getId,
                        user -> user.getNickname() == null ? "" : user.getNickname(), (a, b) -> a));
        List<AdminCustomerServiceRepairRespVO> list = new ArrayList<>(page.getList().size());
        for (CustomerServiceRepairRequestDO request : page.getList()) {
            AdminCustomerServiceRepairRespVO vo = toRespVO(request,
                    memberMap.get(request.getMemberId()), orderMap.get(request.getId()));
            vo.setCreatorId(request.getCreatorId());
            vo.setCreatorName(Objects.equals(CustomerServiceSourceEnum.ADMIN.getSource(), request.getSource())
                    ? adminCreatorMap.get(request.getCreatorId()) : vo.getMemberNickname());
            list.add(vo);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "维修申请详情")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('trade:customer-service:query')")
    public CommonResult<AdminCustomerServiceRepairRespVO> get(@RequestParam("id") Long id) {
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestService.getRepair(id);
        MemberUserRespDTO member = memberUserApi.getUser(request.getMemberId()).getCheckedData();
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderService.getByRepairRequestId(id);
        AdminCustomerServiceRepairRespVO vo = toRespVO(request, member, order);
        vo.setCreatorId(request.getCreatorId());
        if (Objects.equals(CustomerServiceSourceEnum.ADMIN.getSource(), request.getSource())) {
            AdminUserRespDTO creator = request.getCreatorId() == null ? null
                    : adminUserApi.getUser(request.getCreatorId()).getCheckedData();
            vo.setCreatorName(creator == null ? null : creator.getNickname());
        } else {
            vo.setCreatorName(member == null ? null : member.getNickname());
        }
        vo.setLogs(toLogVOs(customerServiceLogService.getLogs(
                CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), id)));
        CustomerServiceAddressDO address = customerServiceAddressService.getAddress();
        if (address != null) {
            AdminCustomerServiceAddressReqVO addressVO = new AdminCustomerServiceAddressReqVO();
            addressVO.setReceiverName(address.getReceiverName());
            addressVO.setReceiverMobile(address.getReceiverMobile());
            addressVO.setAreaName(address.getAreaName());
            addressVO.setDetailAddress(address.getDetailAddress());
            addressVO.setShipRemark(address.getShipRemark());
            vo.setReturnAddress(addressVO);
        }
        return success(vo);
    }

    @PostMapping("/create")
    @Operation(summary = "代客户创建维修申请")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<Long> create(@Valid @RequestBody AdminCustomerServiceRepairCreateReqVO reqVO) {
        customerServiceMemberSupport.validateMember(reqVO.getMemberId());
        CustomerServiceRepairCreateReqBO reqBO = new CustomerServiceRepairCreateReqBO();
        reqBO.setMemberId(reqVO.getMemberId());
        reqBO.setDeviceModel(reqVO.getDeviceModel());
        reqBO.setFaultDesc(reqVO.getFaultDesc());
        reqBO.setContactMobile(reqVO.getContactMobile());
        reqBO.setReceiverName(reqVO.getReceiverName());
        reqBO.setReceiverMobile(reqVO.getReceiverMobile());
        reqBO.setReceiverArea(reqVO.getReceiverArea());
        reqBO.setReceiverDetail(reqVO.getReceiverDetail());
        reqBO.setImages(reqVO.getImages());
        reqBO.setSource(CustomerServiceSourceEnum.ADMIN.getSource());
        reqBO.setCreatorId(getLoginUserId());
        return success(customerServiceRepairRequestService.createRepair(reqBO));
    }

    @PutMapping("/contact")
    @Operation(summary = "记录电话联系结果")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<Boolean> contact(@Valid @RequestBody AdminCustomerServiceRepairContactReqVO reqVO) {
        customerServiceRepairOrderService.recordContact(reqVO.getId(), reqVO.getContactResult(), getLoginUserId());
        return success(true);
    }

    @PostMapping("/order/create")
    @Operation(summary = "生成维修订单")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<AdminCustomerServiceRepairOrderCreateRespVO> createOrder(
            @Valid @RequestBody AdminCustomerServiceRepairOrderCreateReqVO reqVO) {
        Long orderId = customerServiceRepairOrderService.createOrder(reqVO.getRepairRequestId(),
                reqVO.getRepairFee(), reqVO.getReturnFreight(), reqVO.getFeeRemark(), getLoginUserId());
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderService.getByRepairRequestId(
                reqVO.getRepairRequestId());
        AdminCustomerServiceRepairOrderCreateRespVO resp = new AdminCustomerServiceRepairOrderCreateRespVO();
        resp.setRepairOrderId(orderId);
        resp.setPayableAmount(order.getPayableAmount());
        resp.setPayStatus(order.getPayStatus());
        return success(resp);
    }

    @PutMapping("/status")
    @Operation(summary = "推进维修状态")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody AdminCustomerServiceRepairStatusReqVO reqVO) {
        customerServiceRepairRequestService.updateStatus(reqVO.getId(), reqVO.getStatus(),
                reqVO.getRemark(), getLoginUserId());
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "取消维修申请")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<Boolean> cancel(@Valid @RequestBody AdminCustomerServiceRepairCancelReqVO reqVO) {
        customerServiceRepairOrderService.cancel(reqVO.getId(), reqVO.getReason(), getLoginUserId());
        return success(true);
    }

    private List<AdminCustomerServiceLogRespVO> toLogVOs(List<CustomerServiceLogDO> logs) {
        if (CollUtil.isEmpty(logs)) {
            return List.of();
        }
        List<Long> adminIds = logs.stream()
                .filter(log -> Objects.equals(2, log.getOperatorType()))
                .map(CustomerServiceLogDO::getOperatorId).filter(Objects::nonNull).distinct().toList();
        List<Long> memberIds = logs.stream()
                .filter(log -> Objects.equals(1, log.getOperatorType()))
                .map(CustomerServiceLogDO::getOperatorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> adminNames = adminIds.isEmpty() ? Map.of()
                : adminUserApi.getUserList(adminIds).getCheckedData().stream()
                .collect(Collectors.toMap(AdminUserRespDTO::getId,
                        user -> user.getNickname() == null ? "" : user.getNickname(), (a, b) -> a));
        Map<Long, String> memberNames = memberIds.isEmpty() ? Map.of()
                : memberUserApi.getUserList(memberIds).getCheckedData().stream()
                .collect(Collectors.toMap(MemberUserRespDTO::getId,
                        user -> user.getNickname() == null ? "" : user.getNickname(), (a, b) -> a));
        return logs.stream().map(log -> {
            AdminCustomerServiceLogRespVO logVO = new AdminCustomerServiceLogRespVO();
            logVO.setAction(log.getAction());
            logVO.setFromStatus(log.getFromStatus());
            logVO.setToStatus(log.getToStatus());
            logVO.setRemark(log.getRemark());
            logVO.setOperatorType(log.getOperatorType());
            logVO.setOperatorId(log.getOperatorId());
            logVO.setOperatorName(Objects.equals(2, log.getOperatorType())
                    ? adminNames.get(log.getOperatorId())
                    : Objects.equals(1, log.getOperatorType())
                    ? memberNames.get(log.getOperatorId()) : "系统");
            logVO.setCreateTime(log.getCreateTime());
            return logVO;
        }).toList();
    }

    private Map<Long, MemberUserRespDTO> getMemberMap(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Map.of();
        }
        return memberUserApi.getUserList(ids).getCheckedData().stream()
                .collect(Collectors.toMap(MemberUserRespDTO::getId, user -> user, (a, b) -> a));
    }

    private AdminCustomerServiceRepairRespVO toRespVO(CustomerServiceRepairRequestDO request,
                                                      MemberUserRespDTO member) {
        return toRespVO(request, member, null);
    }

    private AdminCustomerServiceRepairRespVO toRespVO(CustomerServiceRepairRequestDO request,
                                                      MemberUserRespDTO member,
                                                      CustomerServiceRepairOrderDO order) {
        AdminCustomerServiceRepairRespVO vo = new AdminCustomerServiceRepairRespVO();
        vo.setId(request.getId());
        vo.setMemberId(request.getMemberId());
        vo.setMemberNickname(member == null ? null : member.getNickname());
        vo.setMemberMobile(member == null ? null : member.getMobile());
        vo.setDeviceModel(request.getDeviceModel());
        vo.setFaultDesc(request.getFaultDesc());
        vo.setContactMobile(request.getContactMobile());
        vo.setReceiverName(request.getReceiverName());
        vo.setReceiverMobile(request.getReceiverMobile());
        vo.setReceiverArea(request.getReceiverArea());
        vo.setReceiverDetail(request.getReceiverDetail());
        vo.setImages(StrUtil.isBlank(request.getImages()) ? null
                : JsonUtils.parseArray(request.getImages(), String.class));
        vo.setStatus(request.getStatus());
        vo.setStatusName(CustomerServiceRepairStatusEnum.nameOf(request.getStatus()));
        vo.setContactTime(request.getContactTime());
        vo.setContactResult(request.getContactResult());
        vo.setShipCompany(request.getShipCompany());
        vo.setShipNo(request.getShipNo());
        vo.setShipTime(request.getShipTime());
        vo.setSource(request.getSource());
        vo.setSourceName(CustomerServiceSourceEnum.nameOf(request.getSource()));
        vo.setCreateTime(request.getCreateTime());
        if (order != null) {
            vo.setRepairOrderId(order.getId());
            vo.setRepairFee(order.getRepairFee());
            vo.setReturnFreight(order.getReturnFreight());
            vo.setPayableAmount(order.getPayableAmount());
            vo.setFeeRemark(order.getFeeRemark());
            vo.setPayStatus(order.getPayStatus());
            vo.setPayStatusName(CustomerServiceRepairOrderPayStatusEnum.nameOf(order.getPayStatus()));
            vo.setRefundStatus(order.getRefundStatus());
            vo.setRefundStatusName(CustomerServiceRepairOrderRefundStatusEnum.nameOf(order.getRefundStatus()));
        }
        return vo;
    }

}
