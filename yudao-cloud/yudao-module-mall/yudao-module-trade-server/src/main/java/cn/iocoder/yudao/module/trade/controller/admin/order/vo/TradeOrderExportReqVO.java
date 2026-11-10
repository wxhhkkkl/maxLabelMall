package cn.iocoder.yudao.module.trade.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 交易订单导出 Request VO。
 *
 * <p>口径（2026-11-10 所有者拍板）：导出**只按时间范围**筛选，列表上其它筛选条件一律不参与。
 * 故这里只保留 {@link #createTime}，不再复用 {@link TradeOrderPageReqVO} 的其它字段。
 */
@Schema(description = "管理后台 - 交易订单导出 Request VO")
@Data
public class TradeOrderExportReqVO {

    @Schema(description = "下单时间范围", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "导出时间范围不能为空")
    @Size(min = 2, max = 2, message = "导出时间范围必须包含开始和结束时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

    @AssertTrue(message = "导出时间范围的开始时间不能晚于结束时间")
    public boolean isCreateTimeValid() {
        if (createTime == null || createTime.length != 2) {
            return true; // 交给 @NotEmpty / @Size 报错
        }
        return !createTime[0].isAfter(createTime[1]);
    }

}
