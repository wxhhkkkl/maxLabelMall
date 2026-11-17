package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 授权赠送记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LicenseGrantPageReqVO extends PageParam {

    @Schema(description = "对象类型：1 会员 2 企业", example = "1")
    private Integer targetType;

    @Schema(description = "对象编号", example = "88")
    private Long targetId;

    @Schema(description = "操作人编号", example = "1")
    private Long operatorId;

    @Schema(description = "创建时间范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
