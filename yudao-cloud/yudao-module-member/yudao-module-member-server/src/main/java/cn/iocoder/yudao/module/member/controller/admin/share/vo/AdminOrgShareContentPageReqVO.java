package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 企业共享内容分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrgShareContentPageReqVO extends PageParam {

    @Schema(description = "企业编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "企业编号不能为空")
    private Long orgId;

    private Long folderId;
    private Integer contentType;
    private String keyword;

}
