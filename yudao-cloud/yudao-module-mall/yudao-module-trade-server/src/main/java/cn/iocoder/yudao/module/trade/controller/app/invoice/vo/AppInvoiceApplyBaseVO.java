package cn.iocoder.yudao.module.trade.controller.app.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 开票申请的字段基类：**票种 + 抬头类型 + 抬头五件套**
 *
 * 三个入口共用它 —— 下单请求里的 `invoice` 子对象、订单详情补开的请求体、以及抬头簿的新增/修改 ——
 * 于是三处的**校验与落库走的也是同一套代码**（`InvoiceValidator`）。
 *
 * ⚠️ **刻意不加 `@NotNull` / `@NotBlank` 这类 Bean Validation 注解**：
 * 必填矩阵**随票种与抬头类型而变**（企业要名称 + 税号，个人只要名称），而"缺哪一项"要用中文明确告诉客户
 * （FR-156）—— 那两件事都只有 `InvoiceValidator` 知道。两套校验并存只会让报错信息分叉。
 *
 * @author 芋道源码
 */
@Data
public class AppInvoiceApplyBaseVO {

    @Schema(description = "发票类型：1 电子普通发票，2 电子增值税专用发票", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer type;

    @Schema(description = "抬头类型：1 企业，2 个人（个人只要名称，且只能开普票）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer titleKind;

    @Schema(description = "抬头名称（公司名或个人名）", requiredMode = Schema.RequiredMode.REQUIRED, example = "南京路桥机械有限公司")
    private String titleName;

    @Schema(description = "纳税人识别号（企业抬头必填；个人抬头选填，可填身份证号）", example = "91320104660690975E")
    private String taxNo;

    @Schema(description = "注册地址（选填，填了要合法）", example = "南京市玄武区…")
    private String titleAddress;

    @Schema(description = "注册电话（选填，填了要合法）", example = "18913984596")
    private String titlePhone;

    @Schema(description = "开户行及账号（选填，填了要合法）", example = "工商银行南京分行 6222…")
    private String bankName;

    @Schema(description = "接收发票 / 收据的邮箱（选填；后台展示，人工发送）", example = "finance@example.com")
    private String email;

}
