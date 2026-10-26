package cn.iocoder.yudao.module.member.controller.app.org.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AppMemberOrgRespVO {

    private Long orgId;
    private String orgName;
    private Integer role;
    private Integer status;
    private Boolean seatActive;
    private LocalDate seatEndDate;
    private Long memberCount;
    private Integer seatTotal;
    private Integer seatUsed;
    private Integer seatAvailable;
    private LocalDate licenseEndDate;

}
