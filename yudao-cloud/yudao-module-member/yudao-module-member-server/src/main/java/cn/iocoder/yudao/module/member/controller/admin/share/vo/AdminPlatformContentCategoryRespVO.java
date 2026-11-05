package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 平台共享内容分类 Response VO")
@Data
public class AdminPlatformContentCategoryRespVO {

    private Long id;
    private String name;
    private Integer sort;

}
