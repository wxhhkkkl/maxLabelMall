package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - **对公转账收款信息**（008）
 *
 * ⚠️ **不是对公单时整个对象是 `null`**（不是空对象），前端据此决定渲不渲染。
 *
 * 为什么要下发这些：**财务动作必须可追溯**（FR-205）—— 运营除了"这笔到账了"，
 * 还要能看到"**凭哪笔流水、谁确认的、什么时候确认的**"（对账时就靠它）。
 *
 * @author 芋道源码
 */
@Schema(description = "管理后台 - 对公转账收款信息")
@Data
public class TradeOrderOfflinePaymentRespVO {

    @Schema(description = "银行流水号", example = "2026101600012345")
    private String bankSerialNo;

    @Schema(description = "打款方名称", example = "南京路桥机械有限公司")
    private String payerName;

    @Schema(description = "到账时间")
    private LocalDateTime paidTime;

    @Schema(description = "备注", example = "分两笔打的，这是第一笔")
    private String remark;

    @Schema(description = "确认人（后台用户编号）", example = "249")
    private Long confirmUserId;

    /**
     * 确认时间 —— **为空即"还没确认"**（本表不另设状态列，见 data-model §1）
     */
    @Schema(description = "确认时间；为空表示还没确认收款")
    private LocalDateTime confirmTime;

}
