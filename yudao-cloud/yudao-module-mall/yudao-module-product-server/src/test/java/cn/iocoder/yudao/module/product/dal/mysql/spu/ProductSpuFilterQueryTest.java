package cn.iocoder.yudao.module.product.dal.mysql.spu;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.filter.ProductSpuFilterValueDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.filter.ProductSpuFilterValueMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 按筛选维度值查商品（US15 / T284、T285）。
 *
 * 这是"前台点一个值、列表就收窄"背后的那条 SQL，也是本特性**唯一**动到既有链路的动作
 * （既有分页查询上加条件）。所以两条都要钉死：
 *
 *   ① 语义正确：每个已选值一条**等值** EXISTS，跨维度即 AND（Q9=B 单选）；
 *   ② **不传时一个字都不改** —— 这个接口是全站共用的，改错会波及所有商品列表。
 *
 * 直接在 Mapper 层测：查询组装就在这里，而再往上一层（Service）只是把分类展开成 categoryIds。
 */
// ⚠️ 不要 @Import 这两个 Mapper：BaseDbUnitTest 的 Application 已经 @MapperScan 扫过它们，
// 再 Import 一次会变成两个同名 bean（NoUniqueBeanDefinitionException）。Service 才需要 Import。
public class ProductSpuFilterQueryTest extends BaseDbUnitTest {

    @Resource
    private ProductSpuMapper productSpuMapper;
    @Resource
    private ProductSpuFilterValueMapper spuFilterValueMapper;

    private Long spu110And300;   // 110 宽 + 300m 长
    private Long spu100And300;   // 100 宽 + 300m 长
    private Long spu110And70;    // 110 宽 + 70m 长
    private Long spuNoTags;      // 没挂任何标签

    private static final Long VALUE_WIDTH_110 = 3L;
    private static final Long VALUE_WIDTH_100 = 4L;
    private static final Long VALUE_LENGTH_300 = 5L;
    private static final Long VALUE_LENGTH_70 = 6L;

    @BeforeEach
    public void prepareData() {
        spu110And300 = insertSpu("碳带 A", 114L);
        spu100And300 = insertSpu("碳带 B", 114L);
        spu110And70 = insertSpu("碳带 C", 114L);
        spuNoTags = insertSpu("碳带 D", 114L);

        tag(spu110And300, VALUE_WIDTH_110, VALUE_LENGTH_300);
        tag(spu100And300, VALUE_WIDTH_100, VALUE_LENGTH_300);
        tag(spu110And70, VALUE_WIDTH_110, VALUE_LENGTH_70);
    }

    private Long insertSpu(String name, Long categoryId) {
        // 只填 ProductSpuDO 真正有的字段 —— 那几个 DO 里没有的列（bar_code / unit / recommend_*）
        // 在生产库里根本不存在，见 create_tables.sql 里 product_spu 那段的说明
        ProductSpuDO spu = ProductSpuDO.builder()
                .name(name).keyword("").introduction("").description("")
                .categoryId(categoryId).picUrl("").sort(0)
                .status(ProductSpuStatusEnum.ENABLE.getStatus())
                .specType(false).price(100).marketPrice(100).costPrice(100).stock(10)
                .deliveryTemplateId(1L).giveIntegral(0).subCommissionType(false)
                .build();
        productSpuMapper.insert(spu);
        return spu.getId();
    }

    private void tag(Long spuId, Long... filterValueIds) {
        for (Long filterValueId : filterValueIds) {
            spuFilterValueMapper.insert(ProductSpuFilterValueDO.builder()
                    .spuId(spuId).filterValueId(filterValueId).build());
        }
    }

    private List<Long> pageIds(AppProductSpuPageReqVO reqVO) {
        PageResult<ProductSpuDO> result = productSpuMapper.selectPage(reqVO, Set.of(114L));
        return result.getList().stream().map(ProductSpuDO::getId).toList();
    }

    @Test
    public void testFilter_singleValue() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setFilterValueIds(List.of(VALUE_WIDTH_110));

        // 挂了 110 的两件（A、C）；B 是 100、D 没挂 → 都不该出现
        assertEquals(Set.of(spu110And300, spu110And70), Set.copyOf(pageIds(reqVO)));
    }

    /** 跨维度 = AND：既要是 110 宽、又要是 300m 长 → 只剩 A */
    @Test
    public void testFilter_crossDimensionIsAnd() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setFilterValueIds(List.of(VALUE_WIDTH_110, VALUE_LENGTH_300));

        assertEquals(List.of(spu110And300), pageIds(reqVO));
    }

    /** 同时挂多个值的商品，任一值都能把它筛出来 */
    @Test
    public void testFilter_productWithSeveralTagsMatchesEachOfThem() {
        AppProductSpuPageReqVO byLength = new AppProductSpuPageReqVO();
        byLength.setPageNo(1);
        byLength.setPageSize(10);
        byLength.setFilterValueIds(List.of(VALUE_LENGTH_70));

        assertEquals(List.of(spu110And70), pageIds(byLength));
    }

    @Test
    public void testFilter_unknownValueYieldsNothing() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setFilterValueIds(List.of(99999L));

        assertTrue(pageIds(reqVO).isEmpty());
    }

    /**
     * ⚠️ **对既有共享接口的兼容性承诺**：不传 `filterValueIds` 时，
     * 结果与"这个功能不存在"时**完全一致**（四件商品都在）。
     *
     * 这个接口是全站商品列表共用的，多追加一个条件就可能波及别处 —— 所以单独钉一条。
     */
    @Test
    public void testNoFilterValueIds_behavesExactlyAsBefore() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        // 不设置 filterValueIds

        assertEquals(4, pageIds(reqVO).size());
    }

    @Test
    public void testEmptyFilterValueIds_behavesExactlyAsBefore() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setFilterValueIds(List.of()); // 空数组 == 不传

        assertEquals(4, pageIds(reqVO).size());
    }

    /** 筛选与关键词叠加：两者**同时**生效（FR-100） */
    @Test
    public void testFilter_combinesWithKeyword() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setKeyword("碳带 C");
        reqVO.setFilterValueIds(List.of(VALUE_WIDTH_110));

        assertEquals(List.of(spu110And70), pageIds(reqVO));
    }

}
