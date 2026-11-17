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
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceLogRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceTicketCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceTicketHandleReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceTicketPageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceTicketRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceLogDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceTicketDO;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceSourceEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTargetTypeEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTicketStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTicketTypeEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceLogService;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceMemberSupport;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceTicketService;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceTicketCreateReqBO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 管理后台 - 客户服务投诉咨询（021 / US70）
 */
@Tag(name = "管理后台 - 客户服务投诉咨询")
@RestController
@RequestMapping("/trade/customer-service/ticket")
@Validated
public class AdminCustomerServiceTicketController {

    @Resource
    private CustomerServiceTicketService customerServiceTicketService;
    @Resource
    private CustomerServiceLogService customerServiceLogService;
    @Resource
    private CustomerServiceMemberSupport customerServiceMemberSupport;
    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private AdminUserApi adminUserApi;

    @GetMapping("/page")
    @Operation(summary = "投诉咨询分页")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:query')")
    public CommonResult<PageResult<AdminCustomerServiceTicketRespVO>> page(
            @Valid AdminCustomerServiceTicketPageReqVO reqVO) {
        Collection<Long> memberIds = null;
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            List<MemberUserRespDTO> members = customerServiceMemberSupport.search(reqVO.getKeyword());
            memberIds = CollUtil.isEmpty(members) ? null : members.stream()
                    .map(MemberUserRespDTO::getId).collect(Collectors.toSet());
        }
        PageResult<CustomerServiceTicketDO> page = customerServiceTicketService.getTicketPage(
                reqVO.getType(), reqVO.getStatus(), reqVO.getSource(), memberIds,
                reqVO.getKeyword(), reqVO.getCreateTime(), reqVO);
        if (CollUtil.isEmpty(page.getList())) {
            return success(PageResult.empty());
        }
        Map<Long, MemberUserRespDTO> memberMap = getMemberMap(page.getList().stream()
                .map(CustomerServiceTicketDO::getMemberId).collect(Collectors.toSet()));
        Map<Long, AdminUserRespDTO> adminMap = getAdminMap(page.getList().stream()
                .map(CustomerServiceTicketDO::getHandlerId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return success(new PageResult<>(page.getList().stream()
                .map(ticket -> toRespVO(ticket, memberMap.get(ticket.getMemberId()),
                        ticket.getHandlerId() == null ? null : adminMap.get(ticket.getHandlerId())))
                .toList(), page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "投诉咨询详情")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('trade:customer-service:query')")
    public CommonResult<AdminCustomerServiceTicketRespVO> get(@RequestParam("id") Long id) {
        CustomerServiceTicketDO ticket = customerServiceTicketService.getTicket(id);
        MemberUserRespDTO member = memberUserApi.getUser(ticket.getMemberId()).getCheckedData();
        AdminUserRespDTO handler = ticket.getHandlerId() == null ? null
                : adminUserApi.getUser(ticket.getHandlerId()).getCheckedData();
        AdminCustomerServiceTicketRespVO vo = toRespVO(ticket, member, handler);
        vo.setLogs(toLogVOs(customerServiceLogService.getLogs(
                CustomerServiceTargetTypeEnum.TICKET.getType(), id)));
        return success(vo);
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

    @PutMapping("/handle")
    @Operation(summary = "处理投诉咨询")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<Boolean> handle(@Valid @RequestBody AdminCustomerServiceTicketHandleReqVO reqVO) {
        customerServiceTicketService.handleTicket(reqVO.getId(), reqVO.getHandleRemark(), getLoginUserId());
        return success(true);
    }

    @PostMapping("/create")
    @Operation(summary = "代客户创建投诉咨询")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<Long> create(@Valid @RequestBody AdminCustomerServiceTicketCreateReqVO reqVO) {
        customerServiceMemberSupport.validateMember(reqVO.getMemberId());
        CustomerServiceTicketCreateReqBO reqBO = new CustomerServiceTicketCreateReqBO();
        reqBO.setMemberId(reqVO.getMemberId());
        reqBO.setType(reqVO.getType());
        reqBO.setContent(reqVO.getContent());
        reqBO.setContactMobile(reqVO.getContactMobile());
        reqBO.setImages(reqVO.getImages());
        reqBO.setSource(CustomerServiceSourceEnum.ADMIN.getSource());
        reqBO.setCreatorId(getLoginUserId());
        return success(customerServiceTicketService.createTicket(reqBO));
    }

    private Map<Long, MemberUserRespDTO> getMemberMap(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Map.of();
        }
        return memberUserApi.getUserList(ids).getCheckedData().stream()
                .collect(Collectors.toMap(MemberUserRespDTO::getId, user -> user, (a, b) -> a));
    }

    private Map<Long, AdminUserRespDTO> getAdminMap(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Map.of();
        }
        return adminUserApi.getUserList(ids).getCheckedData().stream()
                .collect(Collectors.toMap(AdminUserRespDTO::getId, user -> user, (a, b) -> a));
    }

    private AdminCustomerServiceTicketRespVO toRespVO(CustomerServiceTicketDO ticket,
                                                      MemberUserRespDTO member, AdminUserRespDTO handler) {
        AdminCustomerServiceTicketRespVO vo = new AdminCustomerServiceTicketRespVO();
        vo.setId(ticket.getId());
        vo.setMemberId(ticket.getMemberId());
        vo.setMemberNickname(member == null ? null : member.getNickname());
        vo.setMemberMobile(member == null ? null : member.getMobile());
        vo.setType(ticket.getType());
        vo.setTypeName(CustomerServiceTicketTypeEnum.nameOf(ticket.getType()));
        vo.setContent(ticket.getContent());
        vo.setContactMobile(ticket.getContactMobile());
        vo.setStatus(ticket.getStatus());
        vo.setStatusName(CustomerServiceTicketStatusEnum.nameOf(ticket.getStatus()));
        vo.setImages(StrUtil.isBlank(ticket.getImages()) ? null
                : JsonUtils.parseArray(ticket.getImages(), String.class));
        vo.setHandleRemark(ticket.getHandleRemark());
        vo.setHandlerId(ticket.getHandlerId());
        vo.setHandlerName(handler == null ? null : handler.getNickname());
        vo.setHandleTime(ticket.getHandleTime());
        vo.setSource(ticket.getSource());
        vo.setSourceName(CustomerServiceSourceEnum.nameOf(ticket.getSource()));
        vo.setCreateTime(ticket.getCreateTime());
        return vo;
    }

}
