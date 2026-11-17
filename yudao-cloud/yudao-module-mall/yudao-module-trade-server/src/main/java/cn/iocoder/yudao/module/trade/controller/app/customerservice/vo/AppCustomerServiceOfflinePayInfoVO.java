package cn.iocoder.yudao.module.trade.controller.app.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 对公转账收款信息")
@Data
public class AppCustomerServiceOfflinePayInfoVO {

    @Schema(description = "收款户名")
    private String accountName;
    @Schema(description = "收款账号")
    private String accountNo;
    @Schema(description = "开户行")
    private String bankName;
    @Schema(description = "转账提示")
    private String tip;

}
