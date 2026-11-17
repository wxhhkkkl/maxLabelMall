package cn.iocoder.yudao.module.trade.service.customerservice.bo;

import lombok.Data;

/**
 * 对公转账收款信息（021 / US73）
 */
@Data
public class CustomerServiceOfflinePayInfoBO {

    private String accountName;
    private String accountNo;
    private String bankName;
    private String tip;

}
