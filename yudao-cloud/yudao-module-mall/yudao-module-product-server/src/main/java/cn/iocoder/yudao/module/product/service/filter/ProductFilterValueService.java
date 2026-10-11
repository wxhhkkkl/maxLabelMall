package cn.iocoder.yudao.module.product.service.filter;

import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDeleteImpactRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterValueSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterValueDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;

/**
 * 筛选维度可选值 Service 接口
 */
public interface ProductFilterValueService {

    Long createValue(@Valid ProductFilterValueSaveReqVO createReqVO);

    void updateValue(@Valid ProductFilterValueSaveReqVO updateReqVO);

    /**
     * 删除可选值，并清理商品上对它的关联（FR-092）——
     * 不清理的话会留下指向已删除项的关联，那些商品在后台会显示"挂着一个不存在的标签"。
     */
    void deleteValue(Long id);

    ProductFilterValueDO getValue(Long id);

    /** 按运营设定的顺序返回（FR-112） */
    List<ProductFilterValueDO> getValueListByDimensionId(Long dimensionId);

    List<ProductFilterValueDO> getValueListByDimensionIds(Collection<Long> dimensionIds);

    /**
     * 删除前的影响告知（FR-092）：有多少商品挂着它。
     */
    ProductFilterDeleteImpactRespVO getDeleteImpact(Long id);

}
