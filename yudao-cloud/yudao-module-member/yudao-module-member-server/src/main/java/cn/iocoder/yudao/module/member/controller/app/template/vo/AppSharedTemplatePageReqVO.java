package cn.iocoder.yudao.module.member.controller.app.template.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Schema(description = "用户 App - 平台共享模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppSharedTemplatePageReqVO extends PageParam {

    private String keyword;
    private String category;
    private BigDecimal widthMin;
    private BigDecimal widthMax;
    private BigDecimal heightMin;
    private BigDecimal heightMax;

}
