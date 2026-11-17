package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 授权看板 - 客户明细行
 *
 * ⚠️ 同时有个人订阅与企业席位的会员：`personalEndTime` 与 `orgEndTime` 都给，
 * `endTime` 是**合并后的实际有效到期**（取较晚，沿用 012 FR-309）。
 */
@Data
public class LicenseCustomerRespVO {

    @Schema(description = "客户类型：1 个人 2 企业", example = "1")
    private Integer targetType;

    @Schema(description = "客户编号（会员编号或企业编号）", example = "88")
    private Long targetId;

    @Schema(description = "客户名称（会员昵称或企业名）", example = "小王")
    private String targetName;

    @Schema(description = "手机号（个人）", example = "13800008888")
    private String targetMobile;

    @Schema(description = "所属企业名（有企业席位时）", example = "南京路桥机械有限公司")
    private String orgName;

    /** 来源：ORDER 订单购买 / GRANT 运营赠送 / ORG_SEAT 企业席位 */
    @Schema(description = "授权来源", example = "[\"ORDER\",\"ORG_SEAT\"]")
    private List<String> sources;

    @Schema(description = "授权开始时间")
    private LocalDateTime startTime;

    @Schema(description = "到期时间（最后有效日，已按 −1 天处理）")
    private LocalDateTime endTime;

    @Schema(description = "剩余天数；已过期时为 0", example = "210")
    private Integer remainingDays;

    @Schema(description = "已过期天数；未过期为 0", example = "0")
    private Integer expiredDays;

    @Schema(description = "当前是否有效", example = "true")
    private Boolean active;

    @Schema(description = "个人来源到期时间（无则 null）")
    private LocalDateTime personalEndTime;

    @Schema(description = "企业来源到期时间（无则 null）")
    private LocalDateTime orgEndTime;

    @Schema(description = "还能赠送的月数（0 = 不能送时长）", example = "2")
    private Integer maxGiftMonths;

}
