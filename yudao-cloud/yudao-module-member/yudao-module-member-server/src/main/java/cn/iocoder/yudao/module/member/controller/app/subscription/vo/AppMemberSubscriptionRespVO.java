package cn.iocoder.yudao.module.member.controller.app.subscription.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "用户 App - 当前会员订阅 Response VO")
@Data
public class AppMemberSubscriptionRespVO {

    private Long memberId;
    private Boolean active;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long remainingDays;
    private Integer periodMonths;
    private String sourceOrderNo;

}
