package cn.iocoder.yudao.module.product.service.filter;

import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterOptionRespVO;

import java.util.Collection;
import java.util.List;

/**
 * 商品 ↔ 筛选标签 的关联规则 Service。
 *
 * 为什么单独成一个 Service 而不是塞进 `ProductSpuServiceImpl`：
 *   ① 这里的规则（**辖域**）是"分类级筛选维度"这件事的核心，值得有自己的名字与测试；
 *   ② `ProductSpuServiceImpl` 已经有 4 个依赖（SPU/SKU/品牌/分类）且与分类 Service 存在循环，
 *      再往里塞会让它的保存链路更难测。抽出来之后这套规则可以在 H2 上独立跑。
 */
public interface ProductSpuFilterTagService {

    /**
     * 全量替换一件商品的筛选标签。
     *
     * **先校验辖域、再整体替换**，而不是逐个增删 —— 这一条同时覆盖了两个需求：
     * - **FR-095 辖域**：只保留「值所属维度的归属分类 ∈ 商品分类的祖先链（含自身）」的值，
     *   其余**静默丢弃**（不是报错：运营不该因为改分类而被一个必然失败的保存卡住）；
     * - **FR-097 剔除**：商品改分类后，原先挂的、已不在新祖先链上的标签，会因为"全量替换"
     *   而自然消失 —— 不会留下"既看不见、又还挂着"的孤儿关联。
     *
     * @param spuId          商品编号
     * @param categoryId     商品的**当前**分类（调用方负责保证是改完之后的值）
     * @param filterValueIds 想挂上的值 id；`null` / 空 = 清空全部标签
     */
    void saveSpuFilterValues(Long spuId, Long categoryId, List<Long> filterValueIds);

    /**
     * 某商品当前挂着的筛选值 id（升序）。
     */
    List<Long> getFilterValueIdsBySpuId(Long spuId);

    /**
     * 校验"每个维度至多一个值"（决策 Q9=B：前台同维度**单选**）。
     *
     * 违反时**明确拒绝**，而不是静默取一个 —— 后者会把前端的单选 bug 变成
     * "筛选结果莫名其妙"，没有任何线索可查。
     *
     * @param filterValueIds 前台传来的已选值；`null` / 空视为通过
     */
    void validateSingleValuePerDimension(Collection<Long> filterValueIds);

    /**
     * 某分类下的商品**可挂**的维度与值：范围 = 该分类自身 + 全部祖先分类（Q10=B）。
     *
     * 不带可选值的维度**不会**出现在结果里 —— 渲染一个空的维度没有意义，
     * 前台那条规则（FR-090）在后台这里同样成立。
     */
    List<ProductFilterOptionRespVO> getFilterValueOptionList(Long categoryId);

}
