package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.dal.mysql.subscriptionplan.MaxLabelSubscriptionPlanMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryServiceImpl;
import cn.iocoder.yudao.module.product.service.filter.ProductSpuFilterTagServiceImpl;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MEMBER_SUBSCRIPTION_PLAN_SCOPE_SPU_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 商品保存链路（特性 003 / US14）—— **本次上线出问题后补的**。
 *
 * ## 为什么之前没有这条测试
 * 我把筛选标签的规则抽成了 `ProductSpuFilterTagService` 并测得很干净（10 条全绿），
 * 但**从没测过 `ProductSpuServiceImpl` 自己** —— 也就是"它到底有没有正确地调用那套规则"。
 * 结果 2026-10-11 上线后，所有者一改商品就报服务器异常，而我那 47 条测试一条都没红。
 *
 * **教训**：把逻辑抽出去、把逻辑测透，不等于把**接线**测过。
 * 抽出去反而让接线那一段成了没人看的盲区。
 */
@Import({ProductSpuServiceImpl.class, ProductSpuFilterTagServiceImpl.class,
        ProductCategoryServiceImpl.class})
public class ProductSpuServiceImplSaveTest extends BaseDbUnitTest {

    @Resource
    private ProductSpuService spuService;
    @Resource
    private ProductSpuMapper spuMapper;
    @Resource
    private ProductCategoryMapper categoryMapper;
    @Resource
    private MaxLabelSubscriptionPlanMapper planMapper;

    @MockitoBean
    private ProductSkuService productSkuService;
    @MockitoBean
    private ProductBrandService brandService;

    private static final Long CATEGORY_CARBON = 113L;
    private static final Long CATEGORY_WAX = 114L;

    @BeforeEach
    public void prepareData() {
        // 分类必须到第二级（validateCategory 会校验 getCategoryLevel >= 2）
        categoryMapper.insert(ProductCategoryDO.builder().id(1L).parentId(0L).name("根")
                .picUrl("").sort(0).status(0).build());
        categoryMapper.insert(ProductCategoryDO.builder().id(CATEGORY_CARBON).parentId(1L).name("碳带")
                .picUrl("").sort(0).status(0).build());
        categoryMapper.insert(ProductCategoryDO.builder().id(CATEGORY_WAX).parentId(CATEGORY_CARBON).name("蜡基")
                .picUrl("").sort(0).status(0).build());
    }

    private Long insertSpu(String name, Long categoryId) {
        ProductSpuDO spu = ProductSpuDO.builder()
                .name(name).keyword("").introduction("").description("")
                .categoryId(categoryId).picUrl("").sort(0)
                .status(ProductSpuStatusEnum.ENABLE.getStatus())
                .specType(false).price(100).marketPrice(100).costPrice(100).stock(10)
                .deliveryTemplateId(1L).giveIntegral(0).subCommissionType(false)
                .build();
        spuMapper.insert(spu);
        return spu.getId();
    }

    private ProductSpuSaveReqVO reqVO(Long spuId, Long categoryId, List<Long> filterValueIds) {
        ProductSpuSaveReqVO reqVO = new ProductSpuSaveReqVO();
        reqVO.setId(spuId);
        reqVO.setName("碳带 A");
        reqVO.setCategoryId(categoryId);
        reqVO.setKeyword("").setIntroduction("").setDescription("").setPicUrl("");
        reqVO.setSort(0).setSpecType(false).setGiveIntegral(0).setSubCommissionType(false);
        reqVO.setDeliveryTemplateId(1L); // 测试建表里它是 NOT NULL 且无默认值
        reqVO.setFilterValueIds(filterValueIds);
        reqVO.setSkus(List.of(new ProductSkuSaveReqVO()
                .setPrice(100).setMarketPrice(100).setCostPrice(100).setStock(10)));
        return reqVO;
    }

    /** 改商品：**不带**筛选标签（最常见的路径 —— 大多数分类压根没配维度） */
    @Test
    public void testUpdateSpu_withoutFilterValues() {
        Long spuId = insertSpu("碳带 A", CATEGORY_WAX);

        assertDoesNotThrow(() -> spuService.updateSpu(reqVO(spuId, CATEGORY_WAX, null)));
    }

    /** 改商品：带一个**不属于任何维度**的值（应被静默丢弃，不该炸） */
    @Test
    public void testUpdateSpu_withUnknownFilterValue() {
        Long spuId = insertSpu("碳带 A", CATEGORY_WAX);

        assertDoesNotThrow(() -> spuService.updateSpu(reqVO(spuId, CATEGORY_WAX, List.of(99999L))));
    }

    /** 改商品：已有个人授权计划映射时，不允许改成企业专享（契约 admin-api §4.1） */
    @Test
    public void testUpdateSpu_toEnterpriseOnlyRejectedWhenPersonalPlanExists() {
        Long spuId = insertSpu("会员授权商品", CATEGORY_WAX);
        planMapper.insert(MaxLabelSubscriptionPlanDO.builder()
                .spuId(spuId).skuId(9001L).periodMonths(12).scope(1)
                .enabled(true).sort(0).build());

        ProductSpuSaveReqVO reqVO = reqVO(spuId, CATEGORY_WAX, null);
        reqVO.setEnterpriseOnly(true);

        ServiceException exception = assertThrows(ServiceException.class, () -> spuService.updateSpu(reqVO));
        assertEquals(MEMBER_SUBSCRIPTION_PLAN_SCOPE_SPU_MISMATCH.getCode(), exception.getCode());
    }

    /** 新建商品：同样不该炸 */
    @Test
    public void testCreateSpu_withoutFilterValues() {
        ProductSpuSaveReqVO reqVO = reqVO(null, CATEGORY_WAX, null);

        Long id = assertDoesNotThrow(() -> spuService.createSpu(reqVO));
        assertNotNull(id);
    }

}
