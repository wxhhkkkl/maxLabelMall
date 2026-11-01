package cn.iocoder.yudao.module.trade.controller.admin.customization.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 定制材质分页")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminCustomizationMaterialPageReqVO extends PageParam {

    @Schema(description = "材质名称（模糊）", example = "铜版")
    private String name;

    @Schema(description = "状态：0 启用 1 停用", example = "0")
    private Integer status;

}
