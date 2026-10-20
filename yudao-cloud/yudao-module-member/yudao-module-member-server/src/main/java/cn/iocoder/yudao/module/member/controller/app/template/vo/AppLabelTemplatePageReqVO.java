package cn.iocoder.yudao.module.member.controller.app.template.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户 App - 私有云端模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppLabelTemplatePageReqVO extends PageParam {

    @Schema(description = "名称/关键词", example = "面单")
    private String keyword;

}
