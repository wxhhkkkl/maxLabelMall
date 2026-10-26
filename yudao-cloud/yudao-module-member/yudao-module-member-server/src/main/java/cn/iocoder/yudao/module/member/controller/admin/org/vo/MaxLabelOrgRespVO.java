package cn.iocoder.yudao.module.member.controller.admin.org.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MaxLabelOrgRespVO {

    private Long id;
    private String name;
    private Long ownerMemberId;
    private String ownerNickname;
    private String ownerMobile;
    private Integer status;
    private Integer memberCount;
    private Integer seatTotal;
    private Integer seatUsed;
    private LocalDate licenseEndDate;
    private LocalDateTime createTime;

}
