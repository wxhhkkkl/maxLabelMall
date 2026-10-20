package cn.iocoder.yudao.module.member.controller.admin.subscription.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 会员订阅 Response VO")
@Data
public class MemberSubscriptionRespVO {

    private Long id;
    private Long memberId;
    private Integer periodMonths;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean active;
    private Long remainingDays;
    private Long sourceOrderId;
    private String sourceOrderNo;
    private Boolean refunded;
    private LocalDateTime createTime;

}
