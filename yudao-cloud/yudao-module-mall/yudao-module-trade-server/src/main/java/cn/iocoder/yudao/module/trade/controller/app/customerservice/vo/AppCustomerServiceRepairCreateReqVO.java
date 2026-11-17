package cn.iocoder.yudao.module.trade.controller.app.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 维修申请创建 Request VO")
@Data
public class AppCustomerServiceRepairCreateReqVO {

    @Schema(description = "设备型号", requiredMode = Schema.RequiredMode.REQUIRED, example = "Zebra ZT411")
    @NotBlank(message = "设备型号不能为空")
    @Size(max = 100, message = "设备型号不能超过 100 字")
    private String deviceModel;

    @Schema(description = "故障描述", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "故障描述不能为空")
    @Size(max = 1000, message = "故障描述不能超过 1000 字")
    private String faultDesc;

    @Schema(description = "联系手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800008888")
    @NotBlank(message = "联系手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "请填写 11 位手机号")
    private String contactMobile;

    @Schema(description = "返修收件人", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotBlank(message = "返修收件人不能为空")
    @Size(max = 50, message = "返修收件人不能超过 50 字")
    private String receiverName;

    @Schema(description = "返修联系电话", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800008888")
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
