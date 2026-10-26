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
    /** 权益来源：1 个人订阅 2 企业席位（表示决定 endDate 的来源） */
    private Integer sourceType;
    /** 有效企业席位所属企业；无企业来源时为 null */
    private Long orgId;
    /** 企业名称；无企业来源时为 null */
    private String orgName;

}
