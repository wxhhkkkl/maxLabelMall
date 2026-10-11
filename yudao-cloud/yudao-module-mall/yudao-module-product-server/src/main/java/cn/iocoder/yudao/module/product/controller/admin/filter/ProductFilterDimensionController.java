package cn.iocoder.yudao.module.product.controller.admin.filter;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDeleteImpactRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDimensionListRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDimensionRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDimensionSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterDimensionDO;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.filter.ProductFilterDimensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商品筛选维度")
@RestController
@RequestMapping("/product/filter-dimension")
@Validated
public class ProductFilterDimensionController {

    @Resource
    private ProductFilterDimensionService dimensionService;
    @Resource
    private ProductCategoryService categoryService;

    @GetMapping("/list")
    @Operation(summary = "获得某个分类的筛选维度（分「自有」与「继承」两段）")
    @Parameter(name = "categoryId", description = "商品分类编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:query')")
    public CommonResult<ProductFilterDimensionListRespVO> getDimensionList(@RequestParam("categoryId") Long categoryId) {
        List<ProductFilterDimensionDO> own = dimensionService.getDimensionListByCategoryId(categoryId);

        // 继承段 = 生效清单 - 自有。
        // 前端据此把这一段渲染成**只读**，并标明「继承自「碳带」」（FR-110）——
        // 不标来源的话，运营改了父分类的维度，子分类页会静默变化，无从解释。
        List<ProductFilterDimensionDO> inherited = dimensionService.getEffectiveDimensionList(categoryId).stream()
                .filter(dimension -> !Objects.equals(dimension.getCategoryId(), categoryId))
                .toList();

        ProductFilterDimensionListRespVO respVO = new ProductFilterDimensionListRespVO();
        respVO.setOwn(BeanUtils.toBean(own, ProductFilterDimensionRespVO.class));
        List<ProductFilterDimensionRespVO> inheritedVOs = BeanUtils.toBean(inherited, ProductFilterDimensionRespVO.class);
        for (ProductFilterDimensionRespVO vo : inheritedVOs) {
            ProductCategoryDO from = categoryService.getCategory(vo.getCategoryId());
            vo.setInheritedFromCategoryName(from != null ? from.getName() : null);
        }
        respVO.setInherited(inheritedVOs);
        return success(respVO);
    }

    @PostMapping("/create")
    @Operation(summary = "创建筛选维度")
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:create')")
    public CommonResult<Long> createDimension(@Valid @RequestBody ProductFilterDimensionSaveReqVO createReqVO) {
        return success(dimensionService.createDimension(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新筛选维度（不接受改分类）")
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:update')")
    public CommonResult<Boolean> updateDimension(@Valid @RequestBody ProductFilterDimensionSaveReqVO updateReqVO) {
        dimensionService.updateDimension(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除筛选维度（级联删掉它的可选值与商品关联）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:delete')")
    public CommonResult<Boolean> deleteDimension(@RequestParam("id") Long id) {
        dimensionService.deleteDimension(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除筛选维度")
    @Parameter(name = "ids", description = "编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:delete')")
    public CommonResult<Boolean> deleteDimensionList(@RequestParam("ids") List<Long> ids) {
        dimensionService.deleteDimensionList(ids);
        return success(true);
    }

    @GetMapping("/delete-impact")
    @Operation(summary = "删除前的影响告知（会连带删掉多少个可选值、影响多少个商品）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:filter-dimension:query')")
    public CommonResult<ProductFilterDeleteImpactRespVO> getDeleteImpact(@RequestParam("id") Long id) {
        return success(dimensionService.getDeleteImpact(id));
    }

}
