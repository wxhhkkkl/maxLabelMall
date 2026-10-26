package cn.iocoder.yudao.module.member.api.org.dto;

import lombok.Data;

/**
 * 企业授权批次发放 DTO（支付成功时由 trade 调用）
 */
@Data
public class MemberOrgLicenseGrantReqDTO {

    private Long orgId;
    private Long planId;
    private Long spuId;
    private Long skuId;
    /** 计划授予周期（月） */
    private Integer periodMonths;
    /** 本批席位数（= 订单项数量） */
    private Integer seatsTotal;
    private Long orderId;
    private Long orderItemId;
    private String orderNo;

}
