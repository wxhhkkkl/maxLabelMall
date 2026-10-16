package cn.iocoder.yudao.module.trade.controller.app.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - 发票抬头 Response VO")
@Data
public class AppInvoiceTitleRespVO {

    @Schema(description = "抬头编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Long id;

    @Schema(description = "发票类型：1 电子普通发票，2 电子增值税专用发票", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer type;

    @Schema(description = "抬头类型：1 企业，2 个人（个人只要名称，且只能开普票）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer titleKind;

    @Schema(description = "抬头名称（公司名或个人名）", requiredMode = Schema.RequiredMode.REQUIRED, example = "南京路桥机械有限公司")
    private String titleName;

    @Schema(description = "纳税人识别号", example = "91320104660690975E")
    private String taxNo;

    @Schema(description = "注册地址")
    private String titleAddress;

    @Schema(description = "注册电话")
    private String titlePhone;

    @Schema(description = "开户行及账号")
    private String bankName;

    @Schema(description = "是否默认（结算页会预选它）", example = "true")
    private Boolean defaultStatus;

}
