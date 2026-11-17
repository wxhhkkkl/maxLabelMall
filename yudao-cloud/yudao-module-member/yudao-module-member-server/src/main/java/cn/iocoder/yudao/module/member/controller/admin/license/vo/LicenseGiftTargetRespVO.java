package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 赠送对象行（020 / Q162）
 *
 * 赠送对象可以是**从未有过授权**的客户：此时 `endTime` / `remainingDays` 为空或 0，
 * `maxGiftMonths` 为 12（从今天起算），前端照常允许赠送。
 */
@Data
public class LicenseGiftTargetRespVO {

    @Schema(description = "客户类型：1 个人 2 企业", example = "1")
    private Integer targetType;

    @Schema(description = "客户编号（会员编号或企业编号）", example = "88")
    private Long targetId;

    @Schema(description = "客户名称（会员昵称或企业名）", example = "小王")
    private String targetName;

    @Schema(description = "手机号（个人）", example = "13800008888")
    private String targetMobile;

    @Schema(description = "状态：0 启用 1 停用（企业停用不接受赠送）", example = "0")
    private Integer status;

    @Schema(description = "当前到期时间（最后有效日，已按 −1 天处理）；无有效授权时为 null")
    private LocalDateTime endTime;

    @Schema(description = "剩余天数；无有效授权或已过期时为 0", example = "210")
    private Integer remainingDays;

    @Schema(description = "当前是否有效", example = "true")
    private Boolean active;

    @Schema(description = "还能赠送的月数（0 = 不能送时长）", example = "12")
    private Integer maxGiftMonths;

}
