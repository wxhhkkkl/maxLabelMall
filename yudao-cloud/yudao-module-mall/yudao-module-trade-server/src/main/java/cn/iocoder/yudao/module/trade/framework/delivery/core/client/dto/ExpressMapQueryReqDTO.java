package cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto;

import lombok.Data;

/**
 * 物流地图查询的 Req DTO
 *
 * @author yudao
 */
@Data
public class ExpressMapQueryReqDTO {

    /**
     * 快递公司编码
     */
    private String expressCode;

    /**
     * 运单号
     */
    private String logisticsNo;

    /**
     * 收件人手机号（顺丰、中通等快递公司必填）
     */
    private String phone;

    /**
     * 发货地址（省市区，不带分隔符，如「北京市海淀区」）
     */
    private String from;

    /**
     * 收件地址（省市区，不带分隔符，如「山西省长治市」）
     */
    private String to;

}
