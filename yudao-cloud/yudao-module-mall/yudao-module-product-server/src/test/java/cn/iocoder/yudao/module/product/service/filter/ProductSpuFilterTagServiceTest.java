package cn.iocoder.yudao.module.product.service.filter;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDimensionSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterOptionRespVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterValueSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterDimensionDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryServiceImpl;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FILTER_VALUE_MULTI_IN_DIMENSION;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 商品与筛选标签的关联规则（US14 / T271、T272、T273）。
 *
 * 本类钉住的是**辖域**这条规则：商品能挂哪些维度值，取决于
 * 「它所属分类自身 + 它全部祖先分类」（Q10=B）——
 * 因为运营是在「碳带」上配「宽度」的，而商品挂在叶子「蜡基」上。
 *
 * 两个方向都要测：
 *   · 辖域**内**的（自身、祖先）→ 必须挂得上，否则运营配的东西用不了；
 *   · 辖域**外**的（兄弟分支）→ 必须挂不上，否则会冒出"前台永远筛不到"的孤儿标签。
 *
 * 另一个重点是 **FR-097 的剔除**：商品改分类后，不落在新祖先链上的标签要被清掉。
 */
@Import({ProductSpuFilterTagServiceImpl.class, ProductFilterDimensionServiceImpl.class,
        ProductFilterValueServiceImpl.class, ProductCategoryServiceImpl.class})
public class ProductSpuFilterTagServiceTest extends BaseDbUnitTest {

    @Resource
    private ProductSpuFilterTagService spuFilterTagService;
    @Resource
    private ProductFilterDimensionService dimensionService;
    @Resource
    private ProductFilterValueService valueService;
    @Resource
    private ProductCategoryMapper categoryMapper;
    @MockitoBean
    private ProductSpuService productSpuService;

    private static final Long CATEGORY_CARBON = 113L;   // 碳带（父）
    private static final Long CATEGORY_WAX = 114L;      // 蜡基（子，商品挂在这里）
    private static final Long CATEGORY_PRINTER = 117L;  // 打印机（另一条分支）
    private static final Long SPU_ID = 9001L;

    /** 「碳带」上的「宽度」=110 / 100 */
    private Long widthValue110;
    private Long widthValue100;
    /** 「蜡基」上的「轴心」=1英寸 */
    private Long coreValue1Inch;
    /** 「打印机」上的「类型」=桌面型（另一个分支，不该被蜡基的商品挂上） */
    private Long printerValue;

    @BeforeEach
    public void prepareData() {
        insertCategory(1L, 0L, "根");
        insertCategory(CATEGORY_CARBON, 1L, "碳带");
        insertCategory(CATEGORY_WAX, CATEGORY_CARBON, "蜡基");
        insertCategory(CATEGORY_PRINTER, 1L, "打印机");

        Long width = dimensionService.createDimension(new ProductFilterDimensionSaveReqVO()
                .setCategoryId(CATEGORY_CARBON).setName("宽度").setSort(1));
        widthValue110 = valueService.createValue(new ProductFilterValueSaveReqVO()
                .setDimensionId(width).setName("110").setSort(1));
        widthValue100 = valueService.createValue(new ProductFilterValueSaveReqVO()
                .setDimensionId(width).setName("100").setSort(2));

        Long core = dimensionService.createDimension(new ProductFilterDimensionSaveReqVO()
                .setCategoryId(CATEGORY_WAX).setName("轴心").setSort(1));
        coreValue1Inch = valueService.createValue(new ProductFilterValueSaveReqVO()
                .setDimensionId(core).setName("1英寸").setSort(1));

        Long printerType = dimensionService.createDimension(new ProductFilterDimensionSaveReqVO()
                .setCategoryId(CATEGORY_PRINTER).setName("类型").setSort(1));
        printerValue = valueService.createValue(new ProductFilterValueSaveReqVO()
                .setDimensionId(printerType).setName("桌面型").setSort(1));
    }

    private void insertCategory(Long id, Long parentId, String name) {
        categoryMapper.insert(ProductCategoryDO.builder().id(id).parentId(parentId).name(name)
                .picUrl("").sort(0).status(0).build());
    }

    // ==================== T271 辖域校验 ====================

    @Test
    public void testSave_selfCategoryValueIsAccepted() {
        spuFilterTagService.saveSpuFilterValues(SPU_ID, CATEGORY_WAX, List.of(coreValue1Inch));

        assertEquals(List.of(coreValue1Inch), spuFilterTagService.getFilterValueIdsBySpuId(SPU_ID));
    }

    /** ⚠️ 核心：维度配在**父分类**上，商品挂在**子分类**上 —— 必须挂得上 */
    @Test
    public void testSave_ancestorCategoryValueIsAccepted() {
        spuFilterTagService.saveSpuFilterValues(SPU_ID, CATEGORY_WAX, List.of(widthValue110));

        assertEquals(List.of(widthValue110), spuFilterTagService.getFilterValueIdsBySpuId(SPU_ID));
    }

    /** 另一个分支上的值 → 静默过滤掉，不写入 */
    @Test
    public void testSave_outOfScopeValueIsRejected() {
        spuFilterTagService.saveSpuFilterValues(SPU_ID, CATEGORY_WAX,
                List.of(widthValue110, printerValue));

        List<Long> saved = spuFilterTagService.getFilterValueIdsBySpuId(SPU_ID);
        assertEquals(List.of(widthValue110), saved);
        assertFalse(saved.contains(printerValue), "另一个分支上的值不该被写入");
    }

    @Test
    public void testSave_emptyOrNullClearsAll() {
        spuFilterTagService.saveSpuFilterValues(SPU_ID, CATEGORY_WAX, List.of(widthValue110));

        spuFilterTagService.saveSpuFilterValues(SPU_ID, CATEGORY_WAX, null);

        assertTrue(spuFilterTagService.getFilterValueIdsBySpuId(SPU_ID).isEmpty());
    }

    // ==================== T272 改分类后的剔除 ====================

    /**
     * 商品从「蜡基」改到「打印机」后保存 → 碳带/蜡基上的标签都不再适用，必须被清掉。
     *
     * 不清的话，那些标签既不会在前台出现（分类不在范围内），又会在后台商品页显示成
     * "已选但看不见" —— 典型的无法解释状态（FR-097）。
     */
    @Test
    public void testSave_categoryChangedDropsStaleTags() {
        // 先在「蜡基」下挂上「轴心」
        spuFilterTagService.saveSpuFilterValues(SPU_ID, CATEGORY_WAX, List.of(coreValue1Inch));
        assertEquals(List.of(coreValue1Inch), spuFilterTagService.getFilterValueIdsBySpuId(SPU_ID));

        // 改到「打印机」，并且不勾任何「打印机」的值 → 旧标签应被清空
        spuFilterTagService.saveSpuFilterValues(SPU_ID, CATEGORY_PRINTER, List.of());

        assertTrue(spuFilterTagService.getFilterValueIdsBySpuId(SPU_ID).isEmpty(),
                "改分类后，不属于新分类祖先链的标签必须被移除");
    }

    @Test
    public void testSave_isFullReplaceNotAppend() {
        spuFilterTagService.saveSpuFilterValues(SPU_ID, CATEGORY_WAX, List.of(widthValue110));
        spuFilterTagService.saveSpuFilterValues(SPU_ID, CATEGORY_WAX, List.of(coreValue1Inch));

        assertEquals(List.of(coreValue1Inch), spuFilterTagService.getFilterValueIdsBySpuId(SPU_ID));
    }

    // ==================== T273 可选维度口径 ====================

    // ==================== T286 同维度多值 = 非法请求 ====================

    /**
     * 前台是**单选**（Q9=B）：同一维度传两个值 = 非法请求，必须**明确拒绝**。
     *
     * 为什么不静默取一个：那样前端的单选 bug 会表现为"筛选结果莫名其妙"，
     * 页面上没有任何线索指向"参数传重了"。
     */
    @Test
    public void testValidate_rejectsTwoValuesInSameDimension() {
        // 110 与 100 都是「宽度」下的值 —— 必须用建出来的真实 id，
        // H2 的自增从 1 开始，写死 id 会取到不存在的值，那样"不报错"是假绿
        ServiceException exception = assertThrows(ServiceException.class,
                () -> spuFilterTagService.validateSingleValuePerDimension(
                        List.of(widthValue110, widthValue100)));
        assertEquals(FILTER_VALUE_MULTI_IN_DIMENSION.getCode(), exception.getCode());
    }

    @Test
    public void testValidate_allowsOneValuePerDimension() {
        // 不同维度各一个 —— 合法
        assertDoesNotThrow(() -> spuFilterTagService.validateSingleValuePerDimension(
                List.of(widthValue110, coreValue1Inch)));
    }

    @Test
    public void testValidate_allowsEmptyAndNull() {
        assertDoesNotThrow(() -> spuFilterTagService.validateSingleValuePerDimension(null));
        assertDoesNotThrow(() -> spuFilterTagService.validateSingleValuePerDimension(List.of()));
    }

    @Test
    public void testGetFilterValueOptions_scopeIsSelfPlusAncestors() {
        List<ProductFilterOptionRespVO> options = spuFilterTagService.getFilterValueOptionList(CATEGORY_WAX);

        // 「蜡基」自身配的「轴心」+ 从「碳带」继承来的「宽度」
        assertEquals(2, options.size());
        assertTrue(options.stream().anyMatch(o -> "宽度".equals(o.getDimensionName())));
        assertTrue(options.stream().anyMatch(o -> "轴心".equals(o.getDimensionName())));
        // **不该**出现另一个分支「打印机」上的「类型」
        assertFalse(options.stream().anyMatch(o -> "类型".equals(o.getDimensionName())));
    }

    @Test
    public void testGetFilterValueOptions_carriesSourceCategoryName() {
        List<ProductFilterOptionRespVO> options = spuFilterTagService.getFilterValueOptionList(CATEGORY_WAX);

        ProductFilterOptionRespVO width = options.stream()
                .filter(o -> "宽度".equals(o.getDimensionName())).findFirst().orElseThrow();
        // 后台要显示「（来自「碳带」）」，否则运营不知道这个维度是继承来的
        assertEquals("碳带", width.getFromCategoryName());
        assertEquals(2, width.getValues().size());
    }

    @Test
    public void testGetFilterValueOptions_emptyWhenNothingConfigured() {
        assertTrue(spuFilterTagService.getFilterValueOptionList(888L).isEmpty());
    }

    /** 维度下没有可选值时，那条维度不该出现在选项里（前台/后台都不该出现空的维度） */
    @Test
    public void testGetFilterValueOptions_skipsDimensionWithoutValues() {
        dimensionService.createDimension(new ProductFilterDimensionSaveReqVO()
                .setCategoryId(CATEGORY_WAX).setName("空维度").setSort(9));

        List<ProductFilterOptionRespVO> options = spuFilterTagService.getFilterValueOptionList(CATEGORY_WAX);

        assertFalse(options.stream().anyMatch(o -> "空维度".equals(o.getDimensionName())));
    }

}
