package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 会员个人空间内容分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminMemberShareContentPageReqVO extends PageParam {

    @Schema(description = "会员编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员编号不能为空")
    private Long memberId;

    @Schema(description = "文件夹编号；空=全部、0=根目录、>0=指定文件夹")
    private Long folderId;

    private Integer contentType;
    private String keyword;

}
