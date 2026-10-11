package cn.iocoder.yudao.module.product.controller.app.filter;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterOptionRespVO;
import cn.iocoder.yudao.module.product.controller.app.filter.vo.AppProductFilterDimensionRespVO;
import cn.iocoder.yudao.module.product.service.filter.ProductSpuFilterTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户 App - 商城页筛选条（特性 003 / US15 / T294）。
 *
 * ## 只有一个参数，这是有意的
 * 它**只依赖 `categoryId`**，不接受 `keyword` / `filterValueIds` —— 那是 Q11=B
 * （选项按当前条件动态收敛）才会需要的。Q11=A 之下选项集合只由配置决定，是**静态**的，
 * 所以前端在同一个分类内**只需请求一次**（切分类才重取）。见 research.md R6 / R7。
 *
 * ## 失败语义（FR-106）
 * 这个接口失败 **MUST NOT** 影响商品列表 —— 前端应让筛选条**整块不渲染**并允许重试，
 * 商品列表照常显示。
 *
 * ⚠️ 无需登录：与既有的三个 app 商品接口一致（顾客浏览商品本来就不要求登录）。
 */
@Tag(name = "用户 App - 商城页筛选条")
@RestController
@RequestMapping("/product/filter")
@Validated
public class ProductFilterController {

    @Resource
    private ProductSpuFilterTagService spuFilterTagService;

    @GetMapping("/dimension-list")
    @Operation(summary = "获得某分类下要渲染的筛选维度与可选值")
    @PermitAll // ⚠️ 必须：顾客浏览商品本来就不要求登录，与既有三个 app 商品接口一致。
              // 少了它这个方法会返回 401，而前端会 catch 掉 → 筛选条**静默不出现**，
              // 排查时会误以为是"这个分类没配维度"。（2026-10-11 在线上实测发现）
    @Parameter(name = "categoryId", description = "商品分类编号", required = true, example = "114")
    public CommonResult<List<AppProductFilterDimensionRespVO>> getDimensionList(
            @RequestParam("categoryId") @NotNull(message = "分类编号不能为空") Long categoryId) {
        // 维度沿分类树向下继承（Q10=B）：这里传进来的分类，服务端会连同它的**全部祖先**一起取。
        // 所以「蜡基」页能出现配在「碳带」上的「宽度」。
        List<ProductFilterOptionRespVO> options = spuFilterTagService.getFilterValueOptionList(categoryId);
        return success(options.stream().map(ProductFilterController::toAppVO).toList());
    }

    /**
     * 管理端 VO → App VO。
     *
     * 为什么不直接返回管理端那个：两层各自演进的节奏不同（前台的契约更"窄"，
     * 比如前台不需要 `fromCategoryName`）。多这几行，换的是以后改一边不会连带动另一边。
     */
    private static AppProductFilterDimensionRespVO toAppVO(ProductFilterOptionRespVO option) {
        AppProductFilterDimensionRespVO vo = new AppProductFilterDimensionRespVO();
        vo.setDimensionId(option.getDimensionId());
        vo.setDimensionName(option.getDimensionName());
        vo.setSort(option.getSort());
        vo.setValues(option.getValues().stream().map(value -> {
            AppProductFilterDimensionRespVO.Value valueVO = new AppProductFilterDimensionRespVO.Value();
            valueVO.setValueId(value.getValueId());
            valueVO.setName(value.getName());
            valueVO.setSort(value.getSort());
            return valueVO;
        }).toList());
        return vo;
    }

}
