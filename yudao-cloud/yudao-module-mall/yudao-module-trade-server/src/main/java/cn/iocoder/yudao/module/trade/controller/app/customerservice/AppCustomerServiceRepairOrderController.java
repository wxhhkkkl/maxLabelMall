package cn.iocoder.yudao.module.trade.controller.app.customerservice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceOfflinePayInfoVO;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceRepairOrderPayOfflineRespVO;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceRepairOrderPayOnlineRespVO;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceRepairOrderPayReqVO;
import cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceRepairOrderPayStatusEnum;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceRepairOrderService;
import cn.iocoder.yudao.module.trade.service.customerservice.bo.CustomerServiceOfflinePayInfoBO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 用户 App - 维修订单支付（021 / US73）
 */
@Tag(name = "用户 App - 维修订单支付")
@RestController
@RequestMapping("/trade/customer-service/repair-order")
@Validated
public class AppCustomerServiceRepairOrderController {

    @Resource
    private CustomerServiceRepairOrderService customerServiceRepairOrderService;

    @PostMapping("/pay-online")
    @Operation(summary = "发起维修订单在线支付")
    public CommonResult<AppCustomerServiceRepairOrderPayOnlineRespVO> payOnline(
            @Valid @RequestBody AppCustomerServiceRepairOrderPayReqVO reqVO) {
        Long payOrderId = customerServiceRepairOrderService.payOnline(reqVO.getId(), getLoginUserId());
        AppCustomerServiceRepairOrderPayOnlineRespVO resp = new AppCustomerServiceRepairOrderPayOnlineRespVO();
        resp.setPayOrderId(payOrderId);
        return success(resp);
    }

    @PostMapping("/pay-offline")
    @Operation(summary = "选择对公转账")
    public CommonResult<AppCustomerServiceRepairOrderPayOfflineRespVO> payOffline(
            @Valid @RequestBody AppCustomerServiceRepairOrderPayReqVO reqVO) {
        CustomerServiceOfflinePayInfoBO info = customerServiceRepairOrderService.chooseOffline(
                reqVO.getId(), getLoginUserId());
        AppCustomerServiceRepairOrderPayOfflineRespVO resp = new AppCustomerServiceRepairOrderPayOfflineRespVO();
        resp.setPayStatus(CustomerServiceRepairOrderPayStatusEnum.WAITING_CONFIRM.getStatus());
        AppCustomerServiceOfflinePayInfoVO vo = new AppCustomerServiceOfflinePayInfoVO();
        vo.setAccountName(info.getAccountName());
        vo.setAccountNo(info.getAccountNo());
        vo.setBankName(info.getBankName());
        vo.setTip(info.getTip());
        resp.setOffline(vo);
        return success(resp);
    }

}
