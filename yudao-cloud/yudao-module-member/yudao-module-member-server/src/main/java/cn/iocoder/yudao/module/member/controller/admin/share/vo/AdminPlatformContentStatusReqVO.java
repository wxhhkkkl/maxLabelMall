package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 平台共享内容上下架 Request VO")
@Data
public class AdminPlatformContentStatusReqVO {

    @NotNull(message = "编号不能为空")
    private Long id;

    @NotNull(message = "状态不能为空")
    private Integer status;

}
