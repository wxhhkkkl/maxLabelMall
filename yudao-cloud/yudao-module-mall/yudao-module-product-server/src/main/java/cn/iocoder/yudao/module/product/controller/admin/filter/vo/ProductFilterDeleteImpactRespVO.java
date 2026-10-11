package cn.iocoder.yudao.module.product.controller.admin.filter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除前的影响告知（FR-092）。
 *
 * 删维度会连同它的可选值一起消失，删可选值会让挂着它的商品少一个标签 ——
 * 两件事都**不可撤销**，所以先让后台把数字显示出来再让运营点确认。
 */
@Schema(description = "管理后台 - 筛选维度/可选值 删除影响 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterDeleteImpactRespVO {

    @Schema(description = "会被一并删除的可选值个数", example = "8")
    private Long valueCount;

    @Schema(description = "会被解除关联的商品个数", example = "12")
    private Long spuCount;

}
