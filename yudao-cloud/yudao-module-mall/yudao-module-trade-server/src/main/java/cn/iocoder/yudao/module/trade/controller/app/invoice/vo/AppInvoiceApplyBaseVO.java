package cn.iocoder.yudao.module.trade.controller.app.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 开票申请的字段基类：**票种 + 抬头五件套**
 *
 * 两个入口共用它 —— 下单请求里的 `invoice` 子对象、以及订单详情补开的请求体 ——
 * 于是两处的**校验与落库走的也是同一套代码**（`InvoiceValidator`）。
 *
 * ⚠️ **刻意不加 `@NotNull` / `@NotBlank` 这类 Bean Validation 注解**：
 * 必填矩阵**随票种而变**（普票只要名称，专票五项全要），而"缺哪一项"要用中文明确告诉客户
 * （FR-156）—— 那两件事都只有 `InvoiceValidator` 知道。两套校验并存只会让报错信息分叉。
 *
 * @author 芋道源码
 */
@Data
public class AppInvoiceApplyBaseVO {

    @Schema(description = "发票类型：1 电子普通发票，2 电子增值税专用发票", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer type;

    @Schema(description = "抬头名称（公司名或个人名）", requiredMode = Schema.RequiredMode.REQUIRED, example = "南京路桥机械有限公司")
    private String titleName;

    @Schema(description = "纳税人识别号（专票必填，普票选填）", example = "91320104660690975E")
    private String taxNo;

    @Schema(description = "注册地址（专票必填）", example = "南京市玄武区…")
    private String titleAddress;

    @Schema(description = "注册电话（专票必填）", example = "18913984596")
    private String titlePhone;

    @Schema(description = "开户行及账号（专票必填）", example = "工商银行南京分行 6222…")
    private String bankName;

}
