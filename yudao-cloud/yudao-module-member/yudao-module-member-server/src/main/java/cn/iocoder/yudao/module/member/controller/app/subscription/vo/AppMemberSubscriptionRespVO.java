package cn.iocoder.yudao.module.member.controller.app.subscription.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    /**
     * 当前生效的桌面端版本（标准版 / 专业版 / 企业版），取所有有效来源中的最高档；
     * 无有效订阅时为 null。
     */
    private String edition;
    /** 当前所有有效来源的版本（去重，最高档在前） */
    private List<String> activeEditions;
    /** 权益来源：1 个人订阅 2 企业席位（表示决定 endDate 的来源） */
    private Integer sourceType;
    /** 有效企业席位所属企业；无企业来源时为 null */
    private Long orgId;
    /** 企业名称；无企业来源时为 null */
    private String orgName;

}
