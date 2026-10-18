package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - **确认对公转账收款** Request VO（008 / FR-204）
 *
 * ⚠️ 这是**资金动作**：确认即生效（MUST NOT 二级复核，Q42），
 * 所以要**留下可追溯的痕迹** —— 流水号必填、确认人与确认时间由服务端自动记（FR-205）。
 *
 * 除流水号外**全选填**：财务手上有什么填什么，**不因为"少填一个"而拦着不让确认**
 * —— 拦了就等于把已到账的货压着不发（data-model §1 的同一条口径）。
 *
 * @author 芋道源码
 */
@Schema(description = "管理后台 - 确认对公转账收款 Request VO")
@Data
public class TradeOrderOfflineConfirmReqVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "900")
    @NotNull(message = "订单编号不能为空")
    private Long id;

    /**
     * 银行流水号 —— **必填**（FR-204）。
     * 财务就是靠它对银行流水逐笔核销的，没有它这笔确认就对不上账。
     */
    @Schema(description = "银行流水号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026101600012345")
    @NotEmpty(message = "银行流水号不能为空")
    private String bankSerialNo;

    @Schema(description = "打款方名称", example = "南京路桥机械有限公司")
    private String payerName;

    /** ⚠️ epoch 毫秒（yudao 对 `LocalDateTime` 注册了 `TimestampLocalDateTimeDeserializer`） */
    @Schema(description = "到账时间（财务照银行流水填）", example = "1790486340000")
    private LocalDateTime paidTime;

    @Schema(description = "备注", example = "分两笔打的，这是第一笔")
    private String remark;

}
