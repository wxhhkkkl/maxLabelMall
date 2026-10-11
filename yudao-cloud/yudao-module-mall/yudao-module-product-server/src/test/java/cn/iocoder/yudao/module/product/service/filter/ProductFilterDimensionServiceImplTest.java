package cn.iocoder.yudao.module.product.service.filter;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDimensionSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterValueSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterDimensionDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryServiceImpl;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FILTER_DIMENSION_NAME_DUPLICATE;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FILTER_DIMENSION_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProductFilterDimensionService} 的单元测试（US13 / T258、T260）。
 *
 * 本类的**核心用例是 `testCreateDimension_duplicateAcrossAncestorChain`** ——
 * 重名检测必须查**整条分类祖先链**，而不是只查同一个 `category_id`。
 * 后者在"维度只在本分类生效"的旧口径下是对的，但在现行的 Q10=B（向下继承）下
 * **会漏掉父子重名**，结果是子分类页面上出现两行「宽度」，顾客无从选择。
 */
@Import({ProductFilterDimensionServiceImpl.class, ProductFilterValueServiceImpl.class,
        ProductCategoryServiceImpl.class})
public class ProductFilterDimensionServiceImplTest extends BaseDbUnitTest {

    @Resource
    private ProductFilterDimensionService dimensionService;
    @Resource
    private ProductFilterValueService valueService;
    @Resource
    private ProductCategoryMapper categoryMapper;
    @MockitoBean
    private ProductSpuService productSpuService; // 分类 Impl 注入了它，本测试用不到

    /** 线上真实形状：碳带(113) → 蜡基(114) */
    private static final Long CATEGORY_CARBON = 113L;
    private static final Long CATEGORY_WAX = 114L;

    private void prepareCategories() {
        categoryMapper.insert(ProductCategoryDO.builder().id(1L).parentId(0L).name("根")
                .picUrl("").sort(0).status(0).build());
        categoryMapper.insert(ProductCategoryDO.builder().id(CATEGORY_CARBON).parentId(1L).name("碳带")
                .picUrl("").sort(0).status(0).build());
        categoryMapper.insert(ProductCategoryDO.builder().id(CATEGORY_WAX).parentId(CATEGORY_CARBON).name("蜡基")
                .picUrl("").sort(0).status(0).build());
    }

    private Long createDimension(Long categoryId, String name) {
        return dimensionService.createDimension(new ProductFilterDimensionSaveReqVO()
                .setCategoryId(categoryId).setName(name).setSort(1));
    }

    @Test
    public void testCreateDimension_success() {
        prepareCategories();

        Long id = createDimension(CATEGORY_CARBON, "宽度");

        assertNotNull(id);
        ProductFilterDimensionDO dimension = dimensionService.getDimension(id);
        assertEquals(CATEGORY_CARBON, dimension.getCategoryId());
        assertEquals("宽度", dimension.getName());
    }

    @Test
    public void testCreateDimension_duplicateInSameCategory() {
        prepareCategories();
        createDimension(CATEGORY_CARBON, "宽度");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> createDimension(CATEGORY_CARBON, "宽度"));
        assertEquals(FILTER_DIMENSION_NAME_DUPLICATE.getCode(), exception.getCode());
    }

    /**
     * ⚠️ **本类的核心用例**：父分类已有「宽度」，子分类再建一个同名的 —— 必须被拒。
     *
     * 只查 `category_id` 的实现会放行这一条，然后在「蜡基」页上渲染出两行「宽度」。
     */
    @Test
    public void testCreateDimension_duplicateAcrossAncestorChain() {
        prepareCategories();
        createDimension(CATEGORY_CARBON, "宽度");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> createDimension(CATEGORY_WAX, "宽度"));

        assertEquals(FILTER_DIMENSION_NAME_DUPLICATE.getCode(), exception.getCode());
        // 消息里必须带上"是哪个分类上的"，否则运营不知道该去哪改
        assertTrue(exception.getMessage().contains("碳带"), "错误信息里应指明冲突维度所在的分类名");
        assertTrue(exception.getMessage().contains("宽度"));
    }

    /** 不同分支上的同名维度是允许的 —— 重名只禁在**同一条祖先链**上 */
    @Test
    public void testCreateDimension_sameNameOnDifferentBranch() {
        prepareCategories();
        categoryMapper.insert(ProductCategoryDO.builder().id(117L).parentId(1L).name("打印机")
                .picUrl("").sort(0).status(0).build());

        createDimension(CATEGORY_CARBON, "宽度");
        Long id = createDimension(117L, "宽度"); // 另一条分支，不冲突

        assertNotNull(id);
    }

    @Test
    public void testCreateDimension_nameIsTrimmed() {
        prepareCategories();

        Long id = createDimension(CATEGORY_CARBON, "  宽度  ");

        assertEquals("宽度", dimensionService.getDimension(id).getName());
    }

    @Test
    public void testUpdateDimension_renameAndRenameToDuplicate() {
        prepareCategories();
        Long widthId = createDimension(CATEGORY_CARBON, "宽度");
        createDimension(CATEGORY_CARBON, "长度");

        // 正常改名（改成自己不算冲突）
        dimensionService.updateDimension(new ProductFilterDimensionSaveReqVO()
                .setId(widthId).setName("宽度"));
        assertEquals("宽度", dimensionService.getDimension(widthId).getName());

        // 改成同分类下已有的名字 → 拒绝
        assertThrows(ServiceException.class, () -> dimensionService.updateDimension(
                new ProductFilterDimensionSaveReqVO().setId(widthId).setName("长度")));
    }

    @Test
    public void testUpdateDimension_notExists() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> dimensionService.updateDimension(new ProductFilterDimensionSaveReqVO()
                        .setId(999L).setName("宽度")));
        assertEquals(FILTER_DIMENSION_NOT_EXISTS.getCode(), exception.getCode());
    }

    /** Q10=B：配在「碳带」上的维度，在子分类「蜡基」的**生效清单**里也要有 */
    @Test
    public void testGetEffectiveDimensionList_inheritsFromAncestor() {
        prepareCategories();
        createDimension(CATEGORY_CARBON, "宽度");
        createDimension(CATEGORY_WAX, "长度");

        // 蜡基自身的：只有「长度」
        List<ProductFilterDimensionDO> own = dimensionService.getDimensionListByCategoryId(CATEGORY_WAX);
        assertEquals(1, own.size());
        assertEquals("长度", own.get(0).getName());

        // 蜡基生效的：「宽度」（继承）+「长度」（自有）
        List<ProductFilterDimensionDO> effective = dimensionService.getEffectiveDimensionList(CATEGORY_WAX);
        assertEquals(2, effective.size());
        assertTrue(effective.stream().anyMatch(d -> "宽度".equals(d.getName())));
        assertTrue(effective.stream().anyMatch(d -> "长度".equals(d.getName())));
    }

    /** 约束（FR-091 的另一面）：祖先链上不许重名，这条就保证了两段合起来不会出现重复项 */
    @Test
    public void testGetEffectiveDimensionList_noDuplicateNames() {
        prepareCategories();
        createDimension(CATEGORY_CARBON, "宽度");
        createDimension(CATEGORY_CARBON, "长度");
        createDimension(CATEGORY_WAX, "轴心");

        List<ProductFilterDimensionDO> effective = dimensionService.getEffectiveDimensionList(CATEGORY_WAX);
        assertEquals(3, effective.size());
        assertEquals(3, effective.stream().map(ProductFilterDimensionDO::getName).distinct().count());
    }

    /** FR-092：删除维度要**级联删掉它的可选值**，并报告影响范围 */
    @Test
    public void testDeleteDimension_cascadeAndImpact() {
        prepareCategories();
        Long dimensionId = createDimension(CATEGORY_CARBON, "宽度");
        valueService.createValue(new ProductFilterValueSaveReqVO()
                .setDimensionId(dimensionId).setName("110").setSort(1));
        valueService.createValue(new ProductFilterValueSaveReqVO()
                .setDimensionId(dimensionId).setName("100").setSort(2));

        // 删除前的影响：会连带删掉 2 个可选值
        assertEquals(2L, dimensionService.getDeleteImpact(dimensionId).getValueCount());

        // 删除
        dimensionService.deleteDimension(dimensionId);

        assertNull(dimensionService.getDimension(dimensionId));
        assertTrue(valueService.getValueListByDimensionId(dimensionId).isEmpty());
    }

    @Test
    public void testDeleteDimension_notExists() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> dimensionService.deleteDimension(999L));
        assertEquals(FILTER_DIMENSION_NOT_EXISTS.getCode(), exception.getCode());
    }

}
