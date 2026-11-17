package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 维修申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminCustomerServiceRepairPageReqVO extends PageParam {

    @Schema(description = "状态：0 待联系 10 已联系 20 已开单 30 已寄出 40 维修中 50 已完成 60 已取消")
    private Integer status;
    @Schema(description = "来源：1 客户自助 2 客服代建", example = "1")
    private Integer source;
    @Schema(description = "关键字：会员昵称 / 手机号 / 设备型号", example = "Zebra")
    private String keyword;
    @Schema(description = "提交时间范围")
    private LocalDateTime[] createTime;

}
