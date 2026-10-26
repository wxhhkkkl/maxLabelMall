package cn.iocoder.yudao.module.member.controller.app.org.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AppMemberOrgLicenseRespVO {

    private Long id;
    private Long skuId;
    private Integer periodMonths;
    private Integer seatsTotal;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long remainingDays;
    private String sourceOrderNo;
    private Boolean refunded;
    /** 已折算：剩余时长并入后续批次，只留痕 */
    private Boolean folded;
    private Boolean active;

}
