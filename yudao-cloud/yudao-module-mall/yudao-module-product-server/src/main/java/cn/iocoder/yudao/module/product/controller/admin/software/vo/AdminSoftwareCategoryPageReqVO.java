package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 软件分类分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminSoftwareCategoryPageReqVO extends PageParam {

    @Schema(description = "名称（模糊）")
    private String name;

    @Schema(description = "状态：0 启用 1 停用")
    private Integer status;

}
