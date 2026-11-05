package cn.iocoder.yudao.module.member.controller.admin.share.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 平台共享内容分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPlatformContentPageReqVO extends PageParam {

    private String keyword;
    private Integer contentType;
    private String category;
    private Integer status;

}
