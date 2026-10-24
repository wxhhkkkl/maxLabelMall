package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 发货默认地址 Response VO（010 追加）
 */
@Schema(description = "管理后台 - 发货默认地址 Response VO")
@Data
public class TradeOrderDeliveryDefaultRespVO {

    @Schema(description = "地区编号（省市区）", example = "110108")
    private Integer areaId;

    @Schema(description = "详细地址", example = "西北旺亮甲店永丰中路100号北京人才教育基地红楼107")
    private String address;

}
