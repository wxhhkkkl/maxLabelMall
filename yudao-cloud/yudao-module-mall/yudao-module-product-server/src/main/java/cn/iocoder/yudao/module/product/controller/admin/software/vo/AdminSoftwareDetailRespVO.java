package cn.iocoder.yudao.module.product.controller.admin.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "管理后台 - 软件详情 Response VO（含全部版本）")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminSoftwareDetailRespVO extends AdminSoftwareRespVO {

    @Schema(description = "全部版本（含历史与已下架）")
    private List<AdminSoftwareVersionRespVO> versions;

}
