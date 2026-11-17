package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 投诉咨询 Response VO")
@Data
public class AdminCustomerServiceTicketRespVO {

    @Schema(description = "工单编号", example = "501")
    private Long id;
    @Schema(description = "会员编号", example = "88")
    private Long memberId;
    @Schema(description = "会员昵称", example = "张三")
    private String memberNickname;
    @Schema(description = "会员手机号", example = "13800008888")
    private String memberMobile;
    @Schema(description = "诉求类型：1 投诉 2 咨询", example = "1")
    private Integer type;
    @Schema(description = "诉求类型名", example = "投诉")
    private String typeName;
    @Schema(description = "内容")
    private String content;
    @Schema(description = "联系手机号")
    private String contactMobile;
    @Schema(description = "状态：0 待处理 10 已处理", example = "0")
    private Integer status;
    @Schema(description = "状态名", example = "待处理")
    private String statusName;
    @Schema(description = "图片 URL 数组")
    private List<String> images;
    @Schema(description = "处理说明")
    private String handleRemark;
    @Schema(description = "处理人编号")
    private Long handlerId;
    @Schema(description = "处理人昵称")
    private String handlerName;
    @Schema(description = "处理时间")
    private LocalDateTime handleTime;
    @Schema(description = "来源：1 客户自助 2 客服代建", example = "1")
    private Integer source;
    @Schema(description = "来源名", example = "客户自助")
    private String sourceName;
    @Schema(description = "提交时间")
    private LocalDateTime createTime;
    @Schema(description = "流转记录")
    private List<AdminCustomerServiceLogRespVO> logs;

}
