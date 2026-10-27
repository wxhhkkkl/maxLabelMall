package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 软件分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminSoftwarePageReqVO extends PageParam {

    @Schema(description = "分类编号")
    private Long categoryId;

    @Schema(description = "类型：1 桌面端软件 2 打印驱动")
    private Integer type;

    @Schema(description = "状态：0 启用 1 停用")
    private Integer status;

    @Schema(description = "标题（模糊）")
    private String title;

}
