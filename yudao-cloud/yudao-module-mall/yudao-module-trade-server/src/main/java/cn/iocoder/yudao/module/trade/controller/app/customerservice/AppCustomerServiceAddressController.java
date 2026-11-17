package cn.iocoder.yudao.module.trade.controller.app.customerservice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.app.customerservice.vo.AppCustomerServiceAddressRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceAddressDO;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户 App - 客户服务回收地址（021 / US73）
 */
@Tag(name = "用户 App - 客户服务回收地址")
@RestController
@RequestMapping("/trade/customer-service")
@Validated
public class AppCustomerServiceAddressController {

    @Resource
    private CustomerServiceAddressService customerServiceAddressService;

    @GetMapping("/return-address")
    @Operation(summary = "获得回收邮寄地址")
    public CommonResult<AppCustomerServiceAddressRespVO> getReturnAddress() {
        CustomerServiceAddressDO address = customerServiceAddressService.getAddress();
        if (address == null) {
            return success(null);
        }
        AppCustomerServiceAddressRespVO vo = new AppCustomerServiceAddressRespVO();
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverMobile(address.getReceiverMobile());
        vo.setAreaName(address.getAreaName());
        vo.setDetailAddress(address.getDetailAddress());
        vo.setShipRemark(address.getShipRemark());
        return success(vo);
    }

}
