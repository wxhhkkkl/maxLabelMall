package cn.iocoder.yudao.module.member.controller.admin.org.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MaxLabelOrgLicenseRespVO {

    private Long id;
    private Long orgId;
    private Long planId;
    private Long spuId;
    private Long skuId;
    private Integer periodMonths;
    private Integer seatsTotal;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long remainingDays;
    private Long sourceOrderId;
    private String sourceOrderNo;
    private Boolean refunded;
    /** 已折算：剩余时长并入后续批次，只留痕 */
    private Boolean folded;
    private Boolean active;
    private LocalDateTime createTime;

}
