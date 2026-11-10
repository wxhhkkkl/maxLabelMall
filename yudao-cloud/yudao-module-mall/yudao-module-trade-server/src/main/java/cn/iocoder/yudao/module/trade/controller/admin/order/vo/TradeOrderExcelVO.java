package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import cn.iocoder.yudao.framework.excel.core.annotations.DictFormat;
import cn.iocoder.yudao.framework.excel.core.convert.DictConvert;
import cn.iocoder.yudao.framework.excel.core.convert.MoneyConvert;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 交易订单导出 Excel VO。
 *
 * <p>一笔订单一行：同一订单的多件商品名称合并、数量汇总（2026-11-10 所有者口径）。
 */
@Schema(description = "管理后台 - 交易订单导出 Excel VO")
@Data
@ExcelIgnoreUnannotated
public class TradeOrderExcelVO {

    @Schema(description = "订单号", example = "NO-20261110-0001")
    @ExcelProperty("订单号")
    private String no;

    @Schema(description = "下单时间")
    @ExcelProperty("下单时间")
    private LocalDateTime createTime;

    @Schema(description = "订单状态", example = "10")
    @ExcelProperty(value = "订单状态", converter = DictConvert.class)
    @DictFormat("trade_order_status")
    private Integer status;

    @Schema(description = "支付方式", example = "wx_lite")
    @ExcelProperty(value = "支付方式", converter = DictConvert.class)
    @DictFormat("pay_channel_code")
    private String payChannelCode;

    @Schema(description = "支付时间")
    @ExcelProperty("支付时间")
    private LocalDateTime payTime;

    @Schema(description = "会员昵称", example = "小明")
    @ExcelProperty("会员昵称")
    private String userNickname;

    @Schema(description = "会员编号", example = "88")
    @ExcelProperty("会员编号")
    private Long userId;

    @Schema(description = "下单方式：客户自助 / 后台代客", example = "客户自助")
    @ExcelProperty("下单方式")
    private String orderSource;

    @Schema(description = "代下单人昵称；客户自助为 null", example = "运营小王")
    @ExcelProperty("代下单人")
    private String agentUserName;

    @Schema(description = "商品名称（多件合并）", example = "标签纸、色带")
    @ExcelProperty("商品名称")
    private String productNames;

    @Schema(description = "商品数量（汇总）", example = "3")
    @ExcelProperty("商品数量")
    private Integer productCount;

    @Schema(description = "商品原价（分）", example = "10000")
    @ExcelProperty(value = "商品金额", converter = MoneyConvert.class)
    private Integer totalPrice;

    @Schema(description = "运费（分）", example = "800")
    @ExcelProperty(value = "运费", converter = MoneyConvert.class)
    private Integer deliveryPrice;

    @Schema(description = "优惠金额（分）", example = "500")
    @ExcelProperty(value = "优惠金额", converter = MoneyConvert.class)
    private Integer discountPrice;

    @Schema(description = "实付金额（分）", example = "10300")
    @ExcelProperty(value = "实付金额", converter = MoneyConvert.class)
    private Integer payPrice;

    @Schema(description = "退款金额（分）", example = "0")
    @ExcelProperty(value = "退款金额", converter = MoneyConvert.class)
    private Integer refundPrice;

    @Schema(description = "收货人", example = "张三")
    @ExcelProperty("收货人")
    private String receiverName;

    @Schema(description = "收货手机号", example = "13800138000")
    @ExcelProperty("收货手机号")
    private String receiverMobile;

    @Schema(description = "收货地址（省市区 + 详细地址）", example = "北京市 东城区xx 路 1 号")
    @ExcelProperty("收货地址")
    private String receiverAddress;

    @Schema(description = "配送方式", example = "1")
    @ExcelProperty(value = "配送方式", converter = DictConvert.class)
    @DictFormat("trade_delivery_type")
    private Integer deliveryType;

    @Schema(description = "快递单号", example = "SF123456")
    @ExcelProperty("快递单号")
    private String logisticsNo;

    @Schema(description = "发货时间")
    @ExcelProperty("发货时间")
    private LocalDateTime deliveryTime;

    @Schema(description = "发票状态：未申请 / 待开票 / 已开票 / 已作废", example = "待开票")
    @ExcelProperty("发票状态")
    private String invoiceStatus;

    @Schema(description = "商家备注", example = "加急")
    @ExcelProperty("商家备注")
    private String remark;

}
