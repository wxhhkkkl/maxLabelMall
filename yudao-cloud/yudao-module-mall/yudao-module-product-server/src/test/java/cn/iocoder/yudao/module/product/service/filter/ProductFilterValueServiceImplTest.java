package cn.iocoder.yudao.module.product.service.filter;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterDimensionSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.filter.vo.ProductFilterValueSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductFilterValueDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryServiceImpl;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FILTER_VALUE_NAME_DUPLICATE;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FILTER_VALUE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProductFilterValueService} 的单元测试（US13 / T259）。
 *
 * 重点两条：**同一维度下不许重名**，以及**顺序只认 `sort`**（FR-112 ——
 * 运营怎么排，前台就怎么显示；不得按任何运行时数据重排）。
 *
 * 注意"不同维度下可以同名"是**允许**的：碳带下的「110」与别的分类下的「110」互不相干。
 */
@Import({ProductFilterDimensionServiceImpl.class, ProductFilterValueServiceImpl.class,
        ProductCategoryServiceImpl.class})
public class ProductFilterValueServiceImplTest extends BaseDbUnitTest {

    @Resource
    private ProductFilterDimensionService dimensionService;
    @Resource
    private ProductFilterValueService valueService;
    @Resource
    private ProductCategoryMapper categoryMapper;
    @MockitoBean
    private ProductSpuService productSpuService;

    private Long prepareDimension(String dimensionName) {
        categoryMapper.insert(ProductCategoryDO.builder().id(113L).parentId(0L).name("碳带")
                .picUrl("").sort(0).status(0).build());
        return dimensionService.createDimension(new ProductFilterDimensionSaveReqVO()
                .setCategoryId(113L).setName(dimensionName).setSort(1));
    }

    private Long createValue(Long dimensionId, String name, int sort) {
        return valueService.createValue(new ProductFilterValueSaveReqVO()
                .setDimensionId(dimensionId).setName(name).setSort(sort));
    }

    @Test
    public void testCreateValue_success() {
        Long dimensionId = prepareDimension("宽度");

        Long id = createValue(dimensionId, "110", 1);

        ProductFilterValueDO value = valueService.getValue(id);
        assertNotNull(value);
        assertEquals(dimensionId, value.getDimensionId());
        assertEquals("110", value.getName());
    }

    @Test
    public void testCreateValue_duplicateInSameDimension() {
        Long dimensionId = prepareDimension("宽度");
        createValue(dimensionId, "110", 1);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> createValue(dimensionId, "110", 2));
        assertEquals(FILTER_VALUE_NAME_DUPLICATE.getCode(), exception.getCode());
    }

    /** 不同维度下同名是允许的 —— 重名只在**同一个维度内**禁 */
    @Test
    public void testCreateValue_sameNameInDifferentDimension() {
        Long widthId = prepareDimension("宽度");
        Long lengthId = dimensionService.createDimension(new ProductFilterDimensionSaveReqVO()
                .setCategoryId(113L).setName("长度").setSort(2));

        createValue(widthId, "110", 1);
        Long id = createValue(lengthId, "110", 1);

        assertNotNull(id);
    }

    @Test
    public void testCreateValue_nameIsTrimmed() {
        Long dimensionId = prepareDimension("宽度");

        Long id = createValue(dimensionId, "  110  ", 1);

        assertEquals("110", valueService.getValue(id).getName());
    }

    @Test
    public void testUpdateValue_renameAndRenameToDuplicate() {
        Long dimensionId = prepareDimension("宽度");
        Long v110 = createValue(dimensionId, "110", 1);
        createValue(dimensionId, "100", 2);

        valueService.updateValue(new ProductFilterValueSaveReqVO().setId(v110).setName("120"));
        assertEquals("120", valueService.getValue(v110).getName());

        assertThrows(ServiceException.class, () -> valueService.updateValue(
                new ProductFilterValueSaveReqVO().setId(v110).setName("100")));
    }

    @Test
    public void testUpdateValue_notExists() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> valueService.updateValue(new ProductFilterValueSaveReqVO()
                        .setId(999L).setName("110")));
        assertEquals(FILTER_VALUE_NOT_EXISTS.getCode(), exception.getCode());
    }

    /** FR-112：顺序 = 运营设定的 `sort`，不是插入顺序、更不是商品数 */
    @Test
    public void testGetValueList_orderedBySort() {
        Long dimensionId = prepareDimension("宽度");
        createValue(dimensionId, "90", 3);
        createValue(dimensionId, "40", 1);
        createValue(dimensionId, "70", 2);

        List<ProductFilterValueDO> values = valueService.getValueListByDimensionId(dimensionId);

        assertEquals(List.of("40", "70", "90"), values.stream().map(ProductFilterValueDO::getName).toList());
    }

    @Test
    public void testDeleteValue_success() {
        Long dimensionId = prepareDimension("宽度");
        Long id = createValue(dimensionId, "110", 1);

        valueService.deleteValue(id);

        assertNull(valueService.getValue(id));
    }

    @Test
    public void testDeleteValue_notExists() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> valueService.deleteValue(999L));
        assertEquals(FILTER_VALUE_NOT_EXISTS.getCode(), exception.getCode());
    }

    @Test
    public void testGetValueListByDimensionIds_batch() {
        Long widthId = prepareDimension("宽度");
        Long lengthId = dimensionService.createDimension(new ProductFilterDimensionSaveReqVO()
                .setCategoryId(113L).setName("长度").setSort(2));
        createValue(widthId, "110", 1);
        createValue(lengthId, "300m", 1);

        List<ProductFilterValueDO> values = valueService.getValueListByDimensionIds(List.of(widthId, lengthId));

        assertEquals(2, values.size());
    }

}
