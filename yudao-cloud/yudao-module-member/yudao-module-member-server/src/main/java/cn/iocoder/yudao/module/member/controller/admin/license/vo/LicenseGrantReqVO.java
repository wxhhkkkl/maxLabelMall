package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 授权赠送 Request VO
 *
 * ⚠️ 版本**不在请求里**：个人固定专业版、企业固定企业版（Q158），由服务端决定，
 * 免得运营选出一个"个人送企业版"的非法组合。
 */
@Data
public class LicenseGrantReqVO {

    @Schema(description = "对象类型：1 会员 2 企业", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "赠送对象类型不能为空")
    private Integer targetType;

    @Schema(description = "对象编号（会员编号或企业编号）", requiredMode = Schema.RequiredMode.REQUIRED, example = "88")
    @NotNull(message = "赠送对象不能为空")
    private Long targetId;

    @Schema(description = "内容类型：1 时长 2 席位", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "赠送内容不能为空")
    private Integer contentType;

    @Schema(description = "赠送月数：只能是 1 / 3 / 6 / 12", example = "3")
    private Integer months;

    @Schema(description = "赠送席位数", example = "5")
    @Min(value = 1, message = "赠送席位数必须大于 0")
    @Max(value = 10000, message = "赠送席位数过大")
    private Integer seats;

    @Schema(description = "赠送理由", requiredMode = Schema.RequiredMode.REQUIRED, example = "客户投诉打印机故障，补偿 3 个月")
    @NotEmpty(message = "请填写赠送理由")
    @Size(max = 255, message = "赠送理由最多 255 字")
    private String reason;

}
