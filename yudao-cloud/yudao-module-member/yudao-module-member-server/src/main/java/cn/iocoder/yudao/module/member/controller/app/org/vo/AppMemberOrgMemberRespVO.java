package cn.iocoder.yudao.module.member.controller.app.org.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AppMemberOrgMemberRespVO {

    private Long memberId;
    private String nickname;
    private String mobileMasked;
    private Integer role;
    private Boolean seatActive;
    private LocalDate seatEndDate;
    private LocalDateTime joinedTime;

}
