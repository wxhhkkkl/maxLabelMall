package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import cn.iocoder.yudao.module.trade.controller.app.base.property.AppProductPropertyValueDetailRespVO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户 App - 交易订单结算信息 Response VO")
@Data
public class AppTradeOrderSettlementRespVO {

    @Schema(description = "交易类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1") // 对应 TradeOrderTypeEnum 枚举
    private Integer type;

    /**
     * 本单**实际采用**的配送方式（1 快递发货，2 用户自提，3 虚拟商品）
     *
     * ⚠️ 前端**不要自己判断** —— 请求没指定时由商品自动判定（全是虚拟商品 → 3），
     * 本字段就是判定结果。前端据此决定"要不要收货地址"，并在**下单时原样回传**。
     *
     * 枚举 {@link cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum}
     */
    @Schema(description = "本单实际采用的配送方式：1 快递发货，2 用户自提，3 虚拟商品",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer deliveryType;

    @Schema(description = "购物项数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Item> items;

    @Schema(description = "优惠劵数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Coupon> coupons; // 可用 + 不可用

    @Schema(description = "费用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Price price;

    @Schema(description = "收件地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private Address address;

    /**
     * 是否对客户开放「对公转账」（008）
     *
     * ⚠️ **结算页只靠这一个布尔决定"要不要显示对公转账"** —— 前端 MUST NOT 自己判断，
     * 更 MUST NOT 猜收款账户（账户明细在**订单详情**里给，不下发到这里）。
     */
    @Schema(description = "是否开放对公转账", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean offlinePayEnabled;

    /**
     * 对公转账的提示语（如「请在转账附言里写订单号」）—— 后台可配（FR-210）
     */
    @Schema(description = "对公转账提示语", example = "[[提示语占位]]")
    private String offlinePayTip;

    /**
     * 本单是否含企业授权商品（012 / FR-296）
     *
     * 结算页据此**直接隐藏个人抬头选项**。前端 MUST 用本字段，不能只看 URL 上的
     * `enterprise=1` —— 从购物车或直接打开结算页时 URL 上没有这个参数。
     */
    @Schema(description = "本单是否含企业授权商品", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean enterprise;

    @Schema(description = "已使用的积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer usePoint;

    @Schema(description = "总积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer totalPoint;

    /**
     * 营销活动数组
     *
     * 只对应 {@link TradePriceCalculateRespBO.Price#items} 商品匹配的活动
     */
    private List<TradePriceCalculateRespBO.Promotion> promotions;

    @Schema(description = "购物项")
    @Data
    public static class Item {

        // ========== SPU 信息 ==========

        @Schema(description = "品类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
        private Long categoryId;
        @Schema(description = "SPU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
        private Long spuId;
        @Schema(description = "SPU 名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "Apple iPhone 12")
        private String spuName;

        // ========== SKU 信息 ==========

        @Schema(description = "SKU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
        private Integer skuId;
        @Schema(description = "价格，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
        private Integer price;
        @Schema(description = "图片地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/1.png")
        private String picUrl;

        @Schema(description = "属性数组", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
        private List<AppProductPropertyValueDetailRespVO> properties;

        // ========== 购物车信息 ==========

        @Schema(description = "购物车编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
        private Long cartId;

        @Schema(description = "购买数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Integer count;

    }

    @Schema(description = "费用（合计）")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Price {

        @Schema(description = "商品原价（总），单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "500")
        private Integer totalPrice;

        @Schema(description = "订单优惠（总），单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "66")
        private Integer discountPrice;

        @Schema(description = "运费金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "50")
        private Integer deliveryPrice;

        @Schema(description = "优惠劵减免金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
        private Integer couponPrice;

        @Schema(description = "积分抵扣的金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "50")
        private Integer pointPrice;

        @Schema(description = "VIP 减免金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
        private Integer vipPrice;

        @Schema(description = "实际支付金额（总），单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "450")
        private Integer payPrice;

    }

    @Schema(description = "地址信息")
    @Data
    public static class Address {

        @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Long id;

        @Schema(description = "收件人名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "小王")
        private String name;

        @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15601691300")
        private String mobile;

        @Schema(description = "地区编号", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long areaId;
        @Schema(description = "地区名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "上海上海市普陀区")
        private String areaName;

        @Schema(description = "详细地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "望京悠乐汇 A 座")
        private String detailAddress;

        @Schema(description = "是否默认收件地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        private Boolean defaultStatus;

    }

    @Schema(description = "优惠劵信息")
    @Data
    public static class Coupon {

        @Schema(description = "优惠劵编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Long id;

        @Schema(description = "优惠劵名", requiredMode = Schema.RequiredMode.REQUIRED, example = "春节送送送")
        private String name;

        @Schema(description = "是否设置满多少金额可用", requiredMode = Schema.RequiredMode.REQUIRED, example = "100") // 单位：分；0 - 不限制
        private Integer usePrice;

        @Schema(description = "固定日期 - 生效开始时间")
        private LocalDateTime validStartTime;

        @Schema(description = "固定日期 - 生效结束时间")
        private LocalDateTime validEndTime;

        @Schema(description = "优惠类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Integer discountType;

        @Schema(description = "折扣百分比", example = "80") //  例如说，80% 为 80
        private Integer discountPercent;

        @Schema(description = "优惠金额", example = "10")
        private Integer discountPrice;

        @Schema(description = "折扣上限", example = "100") // 单位：分，仅在 discountType 为 PERCENT 使用
        private Integer discountLimitPrice;

        @Schema(description = "是否可用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        private Boolean match;

        @Schema(description = "不可用原因", example = "优惠劵已过期")
        private String mismatchReason;

    }

}
