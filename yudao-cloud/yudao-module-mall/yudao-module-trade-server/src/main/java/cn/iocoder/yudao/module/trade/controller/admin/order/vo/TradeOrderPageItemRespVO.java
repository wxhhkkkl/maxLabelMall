package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import cn.iocoder.yudao.module.trade.controller.admin.base.member.user.MemberUserRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.base.product.property.ProductPropertyValueDetailRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 交易订单的分页项 Response VO")
@Data
public class TradeOrderPageItemRespVO extends TradeOrderBaseVO {

    @Schema(description = "收件人地区名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "上海 上海市 普陀区")
    private String receiverAreaName;

    @Schema(description = "订单项列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Item> items;

    @Schema(description = "用户信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private MemberUserRespVO user;

    @Schema(description = "推广人信息")
    private MemberUserRespVO brokerageUser;

    /**
     * 是不是**待收款**的对公转账单（008 / FR-209）
     *
     * 列表上把它显示成「待收款」而不是「待支付」，财务一眼能看出"这单在等打款"。
     * ⚠️ 这个字段分不出来就得靠 `payChannelCode`，可那一列在**确认收款前是空的**。
     */
    @Schema(description = "是否待收款（对公转账单且未确认收款）", example = "true")
    private Boolean offlineWaiting;

    @Schema(description = "管理后台 - 交易订单的分页项的订单项目")
    @Data
    public static class Item extends TradeOrderItemBaseVO {

        @Schema(description = "属性列表", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<ProductPropertyValueDetailRespVO> properties;

    }

}
