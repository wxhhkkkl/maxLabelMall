package cn.iocoder.yudao.module.member.controller.app.printrecord.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 打印记录上报 Request VO")
@Data
public class AppPrintRecordCreateReqVO {

    @Schema(description = "桌面端记录 UUID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "记录标识不能为空")
    @Size(max = 36, message = "记录标识不能超过 36 个字符")
    private String recordUid;

    @Schema(description = "设备 UUID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备标识不能为空")
    @Size(max = 36, message = "设备标识不能超过 36 个字符")
    private String deviceId;

    @Schema(description = "打印时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "打印时间不能为空")
    private LocalDateTime printTime;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 255, message = "模板名称不能超过 255 个字符")
    private String templateTitle;

    @Schema(description = "打印方式：driver / command", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "打印方式不能为空")
    private String mode;

    @Schema(description = "计划标签张数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "打印张数不能为空")
    @Min(value = 0, message = "打印张数不能为负数")
    @Max(value = 100000, message = "打印张数超出范围")
    private Integer count;

    @Schema(description = "单签拷贝数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "拷贝数不能为空")
    @Min(value = 0, message = "拷贝数不能为负数")
    @Max(value = 99999, message = "拷贝数超出范围")
    private Integer copies;

    private Long physicalCount;
    private Long sentCount;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "打印状态不能为空")
    private String status;

    @Schema(description = "打印机名称")
    @Size(max = 255, message = "打印机名称不能超过 255 个字符")
    private String printer;

}
