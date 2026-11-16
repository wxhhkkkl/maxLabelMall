package cn.iocoder.yudao.module.product.dal.mysql.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.app.favorite.vo.AppFavoritePageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.favorite.ProductFavoriteDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 收藏分页的浏览等级过滤（019 / FR-537、SC-202）
 */
public class ProductFavoritePageBrowseLevelTest extends BaseDbUnitTest {

    private static final Long USER_ID = 1001L;

    @Resource
    private ProductFavoriteMapper favoriteMapper;
    @Resource
    private ProductSpuMapper spuMapper;

    @BeforeEach
    void prepareData() {
        insertSpu("公开", 0);
        insertSpu("登录", 1);
        insertSpu("等级", 2, 5L);
    }

    private void insertSpu(String name, Integer type, Long... levelIds) {
        ProductSpuDO spu = ProductSpuDO.builder()
                .name(name).keyword("").introduction("").description("")
                .categoryId(114L).picUrl("").sort(0)
                .status(ProductSpuStatusEnum.ENABLE.getStatus())
                .specType(false).price(100).marketPrice(100).costPrice(100).stock(10)
                .deliveryTemplateId(1L).giveIntegral(0).subCommissionType(false)
                .enterpriseOnly(false).browseLevelType(type)
                .browseLevelIds(levelIds.length == 0 ? null : List.of(levelIds))
                .build();
        spuMapper.insert(spu);
        favoriteMapper.insert(ProductFavoriteDO.builder()
                .userId(USER_ID).spuId(spu.getId()).build());
    }

    private AppFavoritePageReqVO req() {
        AppFavoritePageReqVO reqVO = new AppFavoritePageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        return reqVO;
    }

    @Test
    void noLevelExcludesSpecifiedLevelSpu() {
        PageResult<ProductFavoriteDO> result = favoriteMapper
                .selectPageByUserAndTypeExcludingEnterprise(USER_ID, null, req());
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
    }

    @Test
    void matchedLevelIncludesSpecifiedLevelSpu() {
        PageResult<ProductFavoriteDO> result = favoriteMapper
                .selectPageByUserAndTypeExcludingEnterprise(USER_ID, 5L, req());
        assertEquals(3, result.getTotal());
        assertEquals(3, result.getList().size());
    }

    @Test
    void unmatchedLevelExcludesSpecifiedLevelSpu() {
        PageResult<ProductFavoriteDO> result = favoriteMapper
                .selectPageByUserAndTypeExcludingEnterprise(USER_ID, 6L, req());
        assertEquals(2, result.getTotal());
    }

}
