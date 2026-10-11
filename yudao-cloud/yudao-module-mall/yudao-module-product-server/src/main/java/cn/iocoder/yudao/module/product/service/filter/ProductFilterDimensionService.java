package cn.iocoder.yudao.module.product.service.filter;

import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDeleteImpactRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDimensionSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterDimensionDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 商品筛选维度 Service 接口
 */
public interface ProductFilterDimensionService {

    Long createDimension(@Valid ProductFilterDimensionSaveReqVO createReqVO);

    void updateDimension(@Valid ProductFilterDimensionSaveReqVO updateReqVO);

    /**
     * 删除维度：**级联**删掉它下面的全部可选值，以及那些值身上的商品关联（FR-092）。
     */
    void deleteDimension(Long id);

    void deleteDimensionList(List<Long> ids);

    ProductFilterDimensionDO getDimension(Long id);

    /**
     * 某个分类**自身**配置的维度（不含继承来的）。
     *
     * 后台抽屉要把「本分类自有」与「继承自父分类」分两段展示（FR-110），故与下面那个方法分开。
     */
    List<ProductFilterDimensionDO> getDimensionListByCategoryId(Long categoryId);

    /**
     * 某个分类**生效**的全部维度：它自己 + 全部祖先分类上的（Q10=B）。
     *
     * 这是前台渲染筛选条、（以及商品可挂哪些值）的唯一入口。
     */
    List<ProductFilterDimensionDO> getEffectiveDimensionList(Long categoryId);

    /**
     * 删除前的影响告知（FR-092）：会被一并删掉多少个可选值、会解除多少个商品的关联。
     */
    ProductFilterDeleteImpactRespVO getDeleteImpact(Long id);

}
