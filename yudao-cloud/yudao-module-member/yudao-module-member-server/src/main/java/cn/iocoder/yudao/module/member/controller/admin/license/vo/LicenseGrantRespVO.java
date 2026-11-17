package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LicenseGrantRespVO {

    @Schema(description = "赠送记录编号", example = "501")
    private Long grantId;

    @Schema(description = "赠送后的到期时间")
    private LocalDateTime afterEndTime;

    @Schema(description = "赠送后的席位数（席位赠送才有）", example = "25")
    private Integer afterSeats;

}
