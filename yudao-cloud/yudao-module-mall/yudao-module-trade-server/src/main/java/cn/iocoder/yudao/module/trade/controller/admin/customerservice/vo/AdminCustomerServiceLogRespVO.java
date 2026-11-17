package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 客户服务流转记录")
@Data
public class AdminCustomerServiceLogRespVO {

    @Schema(description = "动作")
    private String action;
    @Schema(description = "变更前状态")
    private Integer fromStatus;
    @Schema(description = "变更后状态")
    private Integer toStatus;
    @Schema(description = "说明")
    private String remark;
    @Schema(description = "操作人类型：1 会员 2 管理员 3 系统")
    private Integer operatorType;
    @Schema(description = "操作人编号")
    private Long operatorId;
    @Schema(description = "操作人昵称")
    private String operatorName;
    @Schema(description = "时间")
    private LocalDateTime createTime;

}
