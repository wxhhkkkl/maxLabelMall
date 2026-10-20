package cn.iocoder.yudao.module.member.controller.admin.template.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 共享模板审核分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminSharedTemplatePageReqVO extends PageParam {

    private Integer auditStatus;
    private String keyword;

}
