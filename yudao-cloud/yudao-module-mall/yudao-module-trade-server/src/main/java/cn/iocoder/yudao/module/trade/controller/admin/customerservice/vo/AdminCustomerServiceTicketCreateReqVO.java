package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 代建投诉咨询 Request VO")
@Data
public class AdminCustomerServiceTicketCreateReqVO {

    @Schema(description = "会员编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "88")
    @NotNull(message = "会员编号不能为空")
    private Long memberId;

    @Schema(description = "诉求类型：1 投诉 2 咨询", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "诉求类型不能为空")
    private Integer type;

    @Schema(description = "内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "内容不能为空")
    @Size(max = 1000, message = "内容不能超过 1000 字")
    private String content;

    @Schema(description = "联系手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "联系手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "请填写 11 位手机号")
    private String contactMobile;

    @Schema(description = "图片 URL 数组（≤5 张）")
    private List<String> images;

}
