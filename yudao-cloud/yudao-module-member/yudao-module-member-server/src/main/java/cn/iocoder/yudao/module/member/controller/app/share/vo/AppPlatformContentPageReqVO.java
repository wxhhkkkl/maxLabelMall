package cn.iocoder.yudao.module.member.controller.app.share.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Schema(description = "用户 App - 平台共享内容分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppPlatformContentPageReqVO extends PageParam {

    private String keyword;
    private Integer contentType;
    private String category;
    /** 兼容旧共享模板库的尺寸筛选；仅对模板生效 */
    private BigDecimal widthMin;
    private BigDecimal widthMax;
    private BigDecimal heightMin;
    private BigDecimal heightMax;

}
