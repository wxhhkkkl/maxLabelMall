package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 投诉咨询分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminCustomerServiceTicketPageReqVO extends PageParam {

    @Schema(description = "诉求类型：1 投诉 2 咨询", example = "1")
    private Integer type;
    @Schema(description = "状态：0 待处理 10 已处理", example = "0")
    private Integer status;
    @Schema(description = "来源：1 客户自助 2 客服代建", example = "1")
    private Integer source;
    @Schema(description = "关键字：会员昵称 / 手机号 / 内容", example = "小王")
    private String keyword;
    @Schema(description = "提交时间范围")
    private LocalDateTime[] createTime;

}
