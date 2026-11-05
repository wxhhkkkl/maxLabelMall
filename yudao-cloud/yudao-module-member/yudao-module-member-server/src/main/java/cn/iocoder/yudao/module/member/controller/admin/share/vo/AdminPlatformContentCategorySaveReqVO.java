package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 平台共享内容分类保存 Request VO")
@Data
public class AdminPlatformContentCategorySaveReqVO {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称不能超过 64 个字符")
    private String name;

    private Integer sort;

}
