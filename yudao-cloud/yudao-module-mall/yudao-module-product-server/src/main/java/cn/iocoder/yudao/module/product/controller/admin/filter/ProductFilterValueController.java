package cn.iocoder.yudao.module.product.controller.admin.filter;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDeleteImpactRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterValueRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterValueSaveReqVO;
import cn.iocoder.yudao.module.product.service.filter.ProductFilterValueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 筛选维度可选值")
@RestController
@RequestMapping("/product/filter-value")
@Validated
public class ProductFilterValueController {

    @Resource
    private ProductFilterValueService valueService;

    @GetMapping("/list")
    @Operation(summary = "获得某个维度下的可选值（按运营设定的顺序）")
    @Parameter(name = "dimensionId", description = "筛选维度编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:query')")
    public CommonResult<List<ProductFilterValueRespVO>> getValueList(@RequestParam("dimensionId") Long dimensionId) {
        return success(BeanUtils.toBean(valueService.getValueListByDimensionId(dimensionId),
                ProductFilterValueRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建可选值")
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:create')")
    public CommonResult<Long> createValue(@Valid @RequestBody ProductFilterValueSaveReqVO createReqVO) {
        return success(valueService.createValue(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新可选值")
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:update')")
    public CommonResult<Boolean> updateValue(@Valid @RequestBody ProductFilterValueSaveReqVO updateReqVO) {
        valueService.updateValue(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除可选值（并清理商品上对它的关联）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:delete')")
    public CommonResult<Boolean> deleteValue(@RequestParam("id") Long id) {
        valueService.deleteValue(id);
        return success(true);
    }

    @GetMapping("/delete-impact")
    @Operation(summary = "删除前的影响告知（有多少商品挂着它）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:query')")
    public CommonResult<ProductFilterDeleteImpactRespVO> getDeleteImpact(@RequestParam("id") Long id) {
        return success(valueService.getDeleteImpact(id));
    }

}
