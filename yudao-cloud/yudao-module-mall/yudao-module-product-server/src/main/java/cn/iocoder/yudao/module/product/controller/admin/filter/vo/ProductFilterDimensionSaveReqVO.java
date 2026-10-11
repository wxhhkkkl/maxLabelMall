package cn.iocoder.yudao.module.product.controller.admin.filter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 商品筛选维度 新增/修改 Request VO")
@Data
public class ProductFilterDimensionSaveReqVO {

    @Schema(description = "编号", example = "12")
    private Long id;

    /**
     * 归属分类。
     *
     * ⚠️ **修改接口不接受它** —— 挪分类等于换掉整条继承关系（它的子分类页会跟着变），
     * 本期不支持；见 contracts/admin-api.md §1.2。新增时必填。
     */
    @Schema(description = "归属的商品分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "113")
    @NotNull(message = "归属分类不能为空")
    private Long categoryId;

    @Schema(description = "维度名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "宽度")
    @NotEmpty(message = "维度名称不能为空")
    @Size(max = 32, message = "维度名称长度不能超过 32 个字符")
    private String name;

    @Schema(description = "展示顺序", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;

}
