package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LicenseGrantPageItemRespVO {

    @Schema(description = "赠送记录编号", example = "501")
    private Long id;

    @Schema(description = "对象类型：1 会员 2 企业", example = "1")
    private Integer targetType;

    @Schema(description = "对象编号", example = "88")
    private Long targetId;

    @Schema(description = "对象名称", example = "小王")
    private String targetName;

    @Schema(description = "内容类型：1 时长 2 席位", example = "1")
    private Integer contentType;

    @Schema(description = "赠送版本", example = "professional")
    private String edition;

    @Schema(description = "赠送月数", example = "3")
    private Integer months;

    @Schema(description = "赠送席位数", example = "5")
    private Integer seats;

    @Schema(description = "赠送理由", example = "客户投诉打印机故障，补偿 3 个月")
    private String reason;

    @Schema(description = "赠送前到期时间")
    private LocalDateTime beforeEndTime;

    @Schema(description = "赠送后到期时间")
    private LocalDateTime afterEndTime;

    @Schema(description = "赠送前席位数", example = "20")
    private Integer beforeSeats;

    @Schema(description = "赠送后席位数", example = "25")
    private Integer afterSeats;

    @Schema(description = "操作人编号", example = "1")
    private Long operatorId;

    @Schema(description = "操作人昵称", example = "张经理")
    private String operatorName;

    @Schema(description = "赠送时间")
    private LocalDateTime createTime;

    @Schema(description = "撤回时间；非空 = 已撤回")
    private LocalDateTime revokeTime;

    @Schema(description = "撤回人昵称", example = "张经理")
    private String revokeOperatorName;

    @Schema(description = "撤回理由", example = "误送给该客户")
    private String revokeReason;

}
