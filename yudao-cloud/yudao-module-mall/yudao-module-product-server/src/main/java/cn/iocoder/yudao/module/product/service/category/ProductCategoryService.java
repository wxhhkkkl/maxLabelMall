package cn.iocoder.yudao.module.product.service.category;

import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 商品分类 Service 接口
 *
 * @author 芋道源码
 */
public interface ProductCategoryService {

    /**
     * 创建商品分类
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createCategory(@Valid ProductCategorySaveReqVO createReqVO);

    /**
     * 更新商品分类
     *
     * @param updateReqVO 更新信息
     */
    void updateCategory(@Valid ProductCategorySaveReqVO updateReqVO);

    /**
     * 删除商品分类
     *
     * @param id 编号
     */
    void deleteCategory(Long id);

    /**
     * 获得商品分类
     *
     * @param id 编号
     * @return 商品分类
     */
    ProductCategoryDO getCategory(Long id);

    /**
     * 校验商品分类
     *
     * @param id 分类编号
     */
    void validateCategory(Long id);

    /**
     * 获得商品分类的层级
     *
     * @param id 编号
     * @return 商品分类的层级
     */
    Integer getCategoryLevel(Long id);

    /**
     * 获得商品分类的**辖域**：它自己 + 它的全部祖先分类。
     *
     * 这是「分类级筛选维度」的地基（特性 003，决策 Q10=B）：维度只存一个归属分类，
     * 「向下继承到子分类」不是存储结构，而是在**查询时**由这个方法把祖先链一起算出来。
     * 三处都用它：后台查重名（FR-091）、商品可挂的辖域（FR-095）、前台要渲染哪些维度（FR-098）。
     *
     * 容错约定（都有单测钉住，见 `ProductCategoryScopeTest`）：
     * - 分类不存在 / 链断在半路 → 走到哪算哪，**不抛异常**（调用方据此得到"没配维度"，是正确的默认值）
     * - 数据异常导致 `parentId` 成环 → **不死循环**，有深度上限兜底
     * - 根分类的 `parentId` 是 0，**不会**把 0 这个编号带进结果
     *
     * @param categoryId 分类编号
     * @return 辖域内的分类编号集合，**包含** `categoryId` 自身
     */
    Set<Long> scopeOf(Long categoryId);

    /**
     * 获得商品分类列表
     *
     * @param listReqVO 查询条件
     * @return 商品分类列表
     */
    List<ProductCategoryDO> getCategoryList(ProductCategoryListReqVO listReqVO);

    /**
     * 获得开启状态的商品分类列表
     *
     * @return 商品分类列表
     */
    List<ProductCategoryDO> getEnableCategoryList();

    /**
     * 获得开启状态的商品分类列表，指定编号
     *
     * @param ids 商品分类编号数组
     * @return 商品分类列表
     */
    List<ProductCategoryDO> getEnableCategoryList(List<Long> ids);

    /**
     * 校验商品分类是否有效。如下情况，视为无效：
     * 1. 商品分类编号不存在
     * 2. 商品分类被禁用
     * 3. 商品分类层级校验，必须使用第二级的商品分类及以下
     *
     * @param ids 商品分类编号数组
     */
    void validateCategoryList(Collection<Long> ids);

}
