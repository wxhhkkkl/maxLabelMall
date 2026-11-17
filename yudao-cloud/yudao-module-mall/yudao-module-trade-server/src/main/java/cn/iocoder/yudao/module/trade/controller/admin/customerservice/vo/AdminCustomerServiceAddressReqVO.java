package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 回收邮寄地址 Request VO")
@Data
public class AdminCustomerServiceAddressReqVO {

    @Schema(description = "收件人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件人不能为空")
    @Size(max = 50, message = "收件人不能超过 50 字")
    private String receiverName;

    @Schema(description = "联系电话", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "联系电话不能超过 20 字")
    private String receiverMobile;

    @Schema(description = "省市区", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "省市区不能为空")
    @Size(max = 100, message = "省市区不能超过 100 字")
    private String areaName;

    @Schema(description = "详细地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址不能超过 255 字")
    private String detailAddress;

    @Schema(description = "寄件说明")
    @Size(max = 500, message = "寄件说明不能超过 500 字")
    private String shipRemark;

}
