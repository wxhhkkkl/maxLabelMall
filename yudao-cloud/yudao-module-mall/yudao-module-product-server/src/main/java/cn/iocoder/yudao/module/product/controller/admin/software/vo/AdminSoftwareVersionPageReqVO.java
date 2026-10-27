package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 软件版本分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminSoftwareVersionPageReqVO extends PageParam {

    @Schema(description = "软件编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "软件编号不能为空")
    private Long softwareId;

}
