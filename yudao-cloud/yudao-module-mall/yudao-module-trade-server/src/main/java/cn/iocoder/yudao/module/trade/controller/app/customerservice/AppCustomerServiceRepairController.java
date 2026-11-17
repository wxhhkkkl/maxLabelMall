package cn.iocoder.yudao.module.trade.controller.app.customerservice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceAddressRespVO;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceLogRespVO;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceRepairCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceRepairOrderRespVO;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceRepairRespVO;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceRepairShipReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceAddressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceRepairRequestDO;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderPayStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairStatusEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceSourceEnum;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceTargetTypeEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceAddressService;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceLogService;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairOrderService;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairRequestService;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceRepairCreateReqBO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 用户 App - 客户服务在线维修（021 / US71）
 */
@Tag(name = "用户 App - 客户服务在线维修")
@RestController
@RequestMapping("/trade/customer-service/repair")
@Validated
public class AppCustomerServiceRepairController {

    @Resource
    private CustomerServiceRepairRequestService customerServiceRepairRequestService;
    @Resource
    private CustomerServiceRepairOrderService customerServiceRepairOrderService;
    @Resource
    private CustomerServiceAddressService customerServiceAddressService;
    @Resource
    private CustomerServiceLogService customerServiceLogService;

    @PostMapping("/create")
    @Operation(summary = "提交维修申请")
    public CommonResult<Long> create(@Valid @RequestBody AppCustomerServiceRepairCreateReqVO reqVO) {
        CustomerServiceRepairCreateReqBO reqBO = new CustomerServiceRepairCreateReqBO();
        reqBO.setMemberId(getLoginUserId());
        reqBO.setDeviceModel(reqVO.getDeviceModel());
        reqBO.setFaultDesc(reqVO.getFaultDesc());
        reqBO.setContactMobile(reqVO.getContactMobile());
        reqBO.setReceiverName(reqVO.getReceiverName());
        reqBO.setReceiverMobile(reqVO.getReceiverMobile());
        reqBO.setReceiverArea(reqVO.getReceiverArea());
        reqBO.setReceiverDetail(reqVO.getReceiverDetail());
        reqBO.setImages(reqVO.getImages());
        reqBO.setSource(CustomerServiceSourceEnum.MEMBER.getSource());
        reqBO.setCreatorId(getLoginUserId());
        return success(customerServiceRepairRequestService.createRepair(reqBO));
    }

    @GetMapping("/page")
    @Operation(summary = "我的维修申请分页")
    public CommonResult<PageResult<AppCustomerServiceRepairRespVO>> page(PageParam pageParam) {
        PageResult<CustomerServiceRepairRequestDO> page = customerServiceRepairRequestService.getMyRepairPage(
                getLoginUserId(), pageParam);
        Map<Long, CustomerServiceRepairOrderDO> orderMap = page.getList().isEmpty()
                ? Map.of()
                : customerServiceRepairOrderService.getListByRepairRequestIds(
                        page.getList().stream().map(CustomerServiceRepairRequestDO::getId).toList())
                .stream().collect(Collectors.toMap(
                        CustomerServiceRepairOrderDO::getRepairRequestId, order -> order, (a, b) -> a));
        return success(new PageResult<>(page.getList().stream()
                .map(request -> toRespVO(request, orderMap.get(request.getId()))).toList(), page.getTotal()));
    }

    @PostMapping("/ship")
    @Operation(summary = "标记维修已寄出")
    public CommonResult<Boolean> ship(@Valid @RequestBody AppCustomerServiceRepairShipReqVO reqVO) {
        customerServiceRepairOrderService.markShipped(reqVO.getId(), getLoginUserId(),
                reqVO.getShipCompany(), reqVO.getShipNo());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "我的维修申请详情")
    @Parameter(name = "id", required = true)
    public CommonResult<AppCustomerServiceRepairRespVO> get(@RequestParam("id") Long id) {
        CustomerServiceRepairRequestDO request = customerServiceRepairRequestService.getMyRepair(
                getLoginUserId(), id);
        CustomerServiceRepairOrderDO order = customerServiceRepairOrderService.getByRepairRequestId(id);
        AppCustomerServiceRepairRespVO vo = toRespVO(request, order);
        vo.setReturnAddress(toAddressVO(customerServiceAddressService.getAddress()));
        vo.setLogs(customerServiceLogService.getLogs(
                CustomerServiceTargetTypeEnum.REPAIR_REQUEST.getType(), id).stream().map(log -> {
            AppCustomerServiceLogRespVO logVO = new AppCustomerServiceLogRespVO();
            logVO.setAction(log.getAction());
            logVO.setFromStatus(log.getFromStatus());
            logVO.setToStatus(log.getToStatus());
            logVO.setRemark(log.getRemark());
            logVO.setOperatorType(log.getOperatorType());
            logVO.setCreateTime(log.getCreateTime());
            return logVO;
        }).toList());
        return success(vo);
    }

    private AppCustomerServiceAddressRespVO toAddressVO(CustomerServiceAddressDO address) {
        if (address == null) {
            return null;
        }
        AppCustomerServiceAddressRespVO vo = new AppCustomerServiceAddressRespVO();
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverMobile(address.getReceiverMobile());
        vo.setAreaName(address.getAreaName());
        vo.setDetailAddress(address.getDetailAddress());
        vo.setShipRemark(address.getShipRemark());
        return vo;
    }

    private AppCustomerServiceRepairRespVO toRespVO(CustomerServiceRepairRequestDO request,
                                                    CustomerServiceRepairOrderDO order) {
        AppCustomerServiceRepairRespVO vo = new AppCustomerServiceRepairRespVO();
        vo.setId(request.getId());
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
            vo.setCompleteTime(order.getCompleteTime());
            vo.setRepairOrderPayStatus(order.getPayStatus());
            AppCustomerServiceRepairOrderRespVO orderVO = new AppCustomerServiceRepairOrderRespVO();
            orderVO.setId(order.getId());
            orderVO.setRepairFee(order.getRepairFee());
            orderVO.setReturnFreight(order.getReturnFreight());
            orderVO.setPayableAmount(order.getPayableAmount());
            orderVO.setFeeRemark(order.getFeeRemark());
            orderVO.setPayStatus(order.getPayStatus());
            orderVO.setPayStatusName(vo.getPayStatusName());
            orderVO.setRefundStatus(order.getRefundStatus());
            orderVO.setRefundStatusName(vo.getRefundStatusName());
            vo.setRepairOrder(orderVO);
        }
        return vo;
    }

}
