package cn.iocoder.yudao.module.trade.controller.app.customerservice;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceTicketCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceTicketRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceTicketDO;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceSourceEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTicketStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTicketTypeEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceTicketService;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceTicketCreateReqBO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 用户 App - 客户服务投诉咨询（021 / US70）
 */
@Tag(name = "用户 App - 客户服务投诉咨询")
@RestController
@RequestMapping("/trade/customer-service/ticket")
@Validated
public class AppCustomerServiceTicketController {

    @Resource
    private CustomerServiceTicketService customerServiceTicketService;

    @PostMapping("/create")
    @Operation(summary = "提交投诉咨询")
    public CommonResult<Long> create(@Valid @RequestBody AppCustomerServiceTicketCreateReqVO reqVO) {
        CustomerServiceTicketCreateReqBO reqBO = new CustomerServiceTicketCreateReqBO();
        reqBO.setMemberId(getLoginUserId());
        reqBO.setType(reqVO.getType());
        reqBO.setContent(reqVO.getContent());
        reqBO.setContactMobile(reqVO.getContactMobile());
        reqBO.setImages(reqVO.getImages());
        reqBO.setSource(CustomerServiceSourceEnum.MEMBER.getSource());
        reqBO.setCreatorId(getLoginUserId());
        return success(customerServiceTicketService.createTicket(reqBO));
    }

    @GetMapping("/page")
    @Operation(summary = "我的投诉咨询分页")
    public CommonResult<PageResult<AppCustomerServiceTicketRespVO>> page(PageParam pageParam) {
        PageResult<CustomerServiceTicketDO> page = customerServiceTicketService.getMyTicketPage(
                getLoginUserId(), pageParam);
        return success(new PageResult<>(page.getList().stream().map(this::toRespVO).toList(),
                page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "我的投诉咨询详情")
    @Parameter(name = "id", required = true)
    public CommonResult<AppCustomerServiceTicketRespVO> get(@RequestParam("id") Long id) {
        return success(toRespVO(customerServiceTicketService.getMyTicket(getLoginUserId(), id)));
    }

    private AppCustomerServiceTicketRespVO toRespVO(CustomerServiceTicketDO ticket) {
        AppCustomerServiceTicketRespVO vo = new AppCustomerServiceTicketRespVO();
        vo.setId(ticket.getId());
        vo.setType(ticket.getType());
        vo.setTypeName(CustomerServiceTicketTypeEnum.nameOf(ticket.getType()));
        vo.setContent(ticket.getContent());
        vo.setContactMobile(ticket.getContactMobile());
        vo.setStatus(ticket.getStatus());
        vo.setStatusName(CustomerServiceTicketStatusEnum.nameOf(ticket.getStatus()));
        vo.setHandleRemark(ticket.getHandleRemark());
        vo.setHandleTime(ticket.getHandleTime());
        vo.setImages(StrUtil.isBlank(ticket.getImages()) ? null
                : JsonUtils.parseArray(ticket.getImages(), String.class));
        vo.setCreateTime(ticket.getCreateTime());
        return vo;
    }

}
