package cn.iocoder.yudao.module.trade.controller.app.customization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 用户 App - 定制报价请求
 *
 * 报价不要求上传文件；传了 `addressId` 时一并返回运费（需登录）。
 */
@Schema(description = "用户 App - 定制报价请求")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppCustomizationQuoteReqVO extends AppCustomizationSpecReqVO {

    @Schema(description = "收件地址编号；传了才计算运费", example = "7")
    private Long addressId;

    @Schema(description = "配送方式：1 快递", example = "1")
    private Integer deliveryType;

}
