package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 维修订单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminCustomerServiceRepairOrderPageReqVO extends PageParam {

    @Schema(description = "维修订单编号", example = "701")
    private Long id;
    @Schema(description = "会员编号", example = "88")
    private Long memberId;
    @Schema(description = "支付状态：0 待支付 10 待确认收款 20 已支付 30 无需支付 40 已取消")
    private Integer payStatus;
    @Schema(description = "退款状态：0 未退款 10 退款中 20 已退款 30 退款失败")
    private Integer refundStatus;
    @Schema(description = "创建时间范围")
    private LocalDateTime[] createTime;

}
