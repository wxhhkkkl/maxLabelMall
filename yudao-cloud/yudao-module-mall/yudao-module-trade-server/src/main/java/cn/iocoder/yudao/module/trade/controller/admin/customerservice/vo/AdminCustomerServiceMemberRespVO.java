package cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 客户服务会员检索 Response VO")
@Data
public class AdminCustomerServiceMemberRespVO {

    @Schema(description = "会员编号", example = "88")
    private Long id;
    @Schema(description = "昵称", example = "张三")
    private String nickname;
    @Schema(description = "手机号", example = "13800008888")
    private String mobile;

}
