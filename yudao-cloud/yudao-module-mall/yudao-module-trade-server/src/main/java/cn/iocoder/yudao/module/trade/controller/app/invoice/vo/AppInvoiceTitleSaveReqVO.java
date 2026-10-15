package cn.iocoder.yudao.module.trade.controller.app.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发票抬头簿的新增 / 修改 Request VO
 *
 * ⚠️ **没有票种字段**：一条抬头两种票都能用（`trade_invoice_title` 的注释里写了为什么）。
 * 所以这里**不要求**专票那四项 —— 只开普票的客户不该被迫填开户行，
 * 缺项在他**选择专票时**才由 `InvoiceValidator` 拒掉。
 *
 * @author 芋道源码
 */
@Data
public class AppInvoiceTitleSaveReqVO {

    @Schema(description = "抬头编号（新增时为空）", example = "3")
    private Long id;

    @Schema(description = "抬头名称（公司名或个人名）", requiredMode = Schema.RequiredMode.REQUIRED, example = "南京路桥机械有限公司")
    private String titleName;

    @Schema(description = "纳税人识别号", example = "91320104660690975E")
    private String taxNo;

    @Schema(description = "注册地址", example = "南京市玄武区…")
    private String titleAddress;

    @Schema(description = "注册电话", example = "18913984596")
    private String titlePhone;

    @Schema(description = "开户行及账号", example = "工商银行南京分行 6222…")
    private String bankName;

    @Schema(description = "是否设为默认抬头", example = "true")
    private Boolean defaultStatus;

}
