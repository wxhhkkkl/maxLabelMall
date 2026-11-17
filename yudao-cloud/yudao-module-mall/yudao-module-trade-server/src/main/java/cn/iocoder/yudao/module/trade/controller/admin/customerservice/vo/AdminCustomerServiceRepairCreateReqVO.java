package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 代建维修申请 Request VO")
@Data
public class AdminCustomerServiceRepairCreateReqVO {

    @Schema(description = "会员编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "88")
    @NotNull(message = "会员编号不能为空")
    private Long memberId;

    @Schema(description = "设备型号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备型号不能为空")
    @Size(max = 100, message = "设备型号不能超过 100 字")
    private String deviceModel;

    @Schema(description = "故障描述", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "故障描述不能为空")
    @Size(max = 1000, message = "故障描述不能超过 1000 字")
    private String faultDesc;

    @Schema(description = "联系手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "联系手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "请填写 11 位手机号")
    private String contactMobile;

    @Schema(description = "返修收件人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "返修收件人不能为空")
    @Size(max = 50, message = "返修收件人不能超过 50 字")
    private String receiverName;

    @Schema(description = "返修联系电话", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "返修联系电话不能为空")
    @Size(max = 20, message = "返修联系电话不能超过 20 字")
    private String receiverMobile;

    @Schema(description = "返修省市区", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "返修省市区不能为空")
    @Size(max = 100, message = "返修省市区不能超过 100 字")
    private String receiverArea;

    @Schema(description = "返修详细地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "返修详细地址不能为空")
    @Size(max = 255, message = "返修详细地址不能超过 255 字")
    private String receiverDetail;

    @Schema(description = "图片 URL 数组（≤5 张）")
    private List<String> images;

}
