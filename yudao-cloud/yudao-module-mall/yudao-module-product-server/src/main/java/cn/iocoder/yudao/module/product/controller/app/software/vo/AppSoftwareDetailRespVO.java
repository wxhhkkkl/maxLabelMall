package cn.iocoder.yudao.module.product.controller.app.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "用户 App - 软件详情 Response VO（只含当前发布版本）")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppSoftwareDetailRespVO extends AppSoftwareRespVO {

    @Schema(description = "当前发布版本（每平台/架构一条）")
    private List<AppSoftwareVersionRespVO> versions;

}
