package cn.iocoder.yudao.module.trade.controller.app.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 回收邮寄地址")
@Data
public class AppCustomerServiceAddressRespVO {

    @Schema(description = "收件人")
    private String receiverName;
    @Schema(description = "联系电话")
    private String receiverMobile;
    @Schema(description = "省市区")
    private String areaName;
    @Schema(description = "详细地址")
    private String detailAddress;
    @Schema(description = "寄件说明")
    private String shipRemark;

}
