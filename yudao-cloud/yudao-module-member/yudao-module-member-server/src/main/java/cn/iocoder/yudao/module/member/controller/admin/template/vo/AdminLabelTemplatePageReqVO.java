package cn.iocoder.yudao.module.member.controller.admin.template.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 会员模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminLabelTemplatePageReqVO extends PageParam {

    @Schema(description = "会员编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员编号不能为空")
    private Long userId;

    private String keyword;

}
