package cn.iocoder.yudao.module.member.controller.admin.org.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MaxLabelOrgMemberInfoRespVO {

    private Long orgId;
    private String orgName;
    private Integer role;
    private Boolean seatActive;
    private LocalDate seatEndDate;

}
