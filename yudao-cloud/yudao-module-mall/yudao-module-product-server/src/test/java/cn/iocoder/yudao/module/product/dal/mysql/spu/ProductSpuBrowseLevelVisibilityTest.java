package cn.iocoder.yudao.module.product.dal.mysql.spu;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商品浏览等级 SQL 过滤（019 / FR-522 ~ FR-525、SC-197）
 *
 * 直接在 Mapper 层测：分页过滤与 total 都在这里组装。
 */
public class ProductSpuBrowseLevelVisibilityTest extends BaseDbUnitTest {

    @Resource
    private ProductSpuMapper productSpuMapper;

    private Long publicSpuId;
    private Long loginSpuId;
    private Long levelSpuId;

    @BeforeEach
    void prepareData() {
        publicSpuId = insertSpu("公开商品", 0);
        loginSpuId = insertSpu("登录商品", 1);
        levelSpuId = insertSpu("等级商品", 2, 5L);
    }

    private Long insertSpu(String name, Integer browseLevelType, Long... levelIds) {
        ProductSpuDO spu = ProductSpuDO.builder()
                .name(name).keyword("").introduction("").description("")
                .categoryId(114L).picUrl("").sort(0)
                .status(ProductSpuStatusEnum.ENABLE.getStatus())
                .specType(false).price(100).marketPrice(100).costPrice(100).stock(10)
                .deliveryTemplateId(1L).giveIntegral(0).subCommissionType(false)
                .enterpriseOnly(false)
                .browseLevelType(browseLevelType)
                .browseLevelIds(levelIds.length == 0 ? null : List.of(levelIds))
                .build();
        productSpuMapper.insert(spu);
        return spu.getId();
    }

    private AppProductSpuPageReqVO pageReq() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        return reqVO;
    }

    @Test
    void guestOnlySeesPublic() {
        PageResult<ProductSpuDO> result = productSpuMapper.selectPage(pageReq(), Set.of(), false, null);
        assertEquals(1, result.getTotal());
        assertEquals(publicSpuId, result.getList().get(0).getId());
    }

    @Test
    void loggedInWithoutLevelSeesPublicAndLogin() {
        PageResult<ProductSpuDO> result = productSpuMapper.selectPage(pageReq(), Set.of(), true, null);
        assertEquals(2, result.getTotal());
        assertEquals(Set.of(publicSpuId, loginSpuId),
                Set.copyOf(result.getList().stream().map(ProductSpuDO::getId).toList()));
    }

    @Test
    void matchedLevelSeesAll() {
        PageResult<ProductSpuDO> result = productSpuMapper.selectPage(pageReq(), Set.of(), true, 5L);
        assertEquals(3, result.getTotal());
        assertEquals(3, result.getList().size());
    }

    @Test
    void mismatchedLevelHidesLevelSpu() {
        PageResult<ProductSpuDO> result = productSpuMapper.selectPage(pageReq(), Set.of(), true, 6L);
        assertEquals(2, result.getTotal());
        assertTrue(result.getList().stream().noneMatch(spu -> spu.getId().equals(levelSpuId)));
    }

    @Test
    void searchAlsoAppliesBrowseLevel() {
        AppProductSpuPageReqVO reqVO = pageReq();
        reqVO.setKeyword("商品");
        PageResult<ProductSpuDO> result = productSpuMapper.selectPage(reqVO, Set.of(), false, null);
        assertEquals(1, result.getTotal());
        assertEquals(publicSpuId, result.getList().get(0).getId());
    }

    @Test
    void selectByIdIncludeDeletedReadsBrowseLevelIds() {
        productSpuMapper.deleteById(levelSpuId);
        ProductSpuDO deleted = productSpuMapper.selectByIdIncludeDeleted(levelSpuId);
        assertEquals(2, deleted.getBrowseLevelType());
        assertEquals(List.of(5L), deleted.getBrowseLevelIds());
    }

}
