package cn.iocoder.yudao.module.member.controller.admin.printrecord.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 会员打印记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPrintRecordPageReqVO extends PageParam {

    @Schema(description = "会员编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员编号不能为空")
    private Long userId;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

}
