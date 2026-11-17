package cn.iocoder.yudao.module.trade.controller.admin.customerservice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceAddressReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customerservice.CustomerServiceAddressDO;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceAddressService;
import com.mzt.logapi.starter.annotation.LogRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.trade.enums.customerservice.CustomerServiceLogRecordConstants.*;

/**
 * 管理后台 - 客户服务回收地址（021 / US74）
 */
@Tag(name = "管理后台 - 客户服务回收地址")
@RestController
@RequestMapping("/trade/customer-service")
@Validated
public class AdminCustomerServiceAddressController {

    @Resource
    private CustomerServiceAddressService customerServiceAddressService;

    @GetMapping("/address")
    @Operation(summary = "获得回收邮寄地址")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:query')")
    public CommonResult<AdminCustomerServiceAddressReqVO> getAddress() {
        CustomerServiceAddressDO address = customerServiceAddressService.getAddress();
        if (address == null) {
            return success(null);
        }
        AdminCustomerServiceAddressReqVO vo = new AdminCustomerServiceAddressReqVO();
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverMobile(address.getReceiverMobile());
        vo.setAreaName(address.getAreaName());
        vo.setDetailAddress(address.getDetailAddress());
        vo.setShipRemark(address.getShipRemark());
        return success(vo);
    }

    @PutMapping("/address")
    @Operation(summary = "保存回收邮寄地址")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    @LogRecord(type = CUSTOMER_SERVICE_TYPE, subType = ADDRESS_UPDATE_SUB_TYPE,
            success = ADDRESS_UPDATE_SUCCESS, bizNo = "0")
    public CommonResult<Boolean> updateAddress(@Valid @RequestBody AdminCustomerServiceAddressReqVO reqVO) {
        customerServiceAddressService.updateAddress(CustomerServiceAddressDO.builder()
                .receiverName(reqVO.getReceiverName())
                .receiverMobile(reqVO.getReceiverMobile())
                .areaName(reqVO.getAreaName())
                .detailAddress(reqVO.getDetailAddress())
                .shipRemark(reqVO.getShipRemark())
                .build());
        return success(true);
    }

}
