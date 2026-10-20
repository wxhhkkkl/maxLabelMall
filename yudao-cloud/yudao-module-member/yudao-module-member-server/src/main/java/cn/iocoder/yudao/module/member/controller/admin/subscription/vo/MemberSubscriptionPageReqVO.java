package cn.iocoder.yudao.module.member.controller.admin.subscription.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 会员订阅分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberSubscriptionPageReqVO extends PageParam {

    @Schema(description = "会员编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "会员编号不能为空")
    private Long userId;

}
