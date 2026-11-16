package cn.iocoder.yudao.module.product.dal.mysql.history;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.history.vo.ProductBrowseHistoryPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.history.ProductBrowseHistoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 浏览历史分页的浏览等级过滤（019 / FR-537、SC-202）
 */
public class ProductBrowseHistoryPageBrowseLevelTest extends BaseDbUnitTest {

    private static final Long USER_ID = 1001L;

    @Resource
    private ProductBrowseHistoryMapper historyMapper;
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
        historyMapper.insert(ProductBrowseHistoryDO.builder()
                .userId(USER_ID).spuId(spu.getId()).userDeleted(false).build());
    }

    private ProductBrowseHistoryPageReqVO req() {
        ProductBrowseHistoryPageReqVO reqVO = new ProductBrowseHistoryPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setUserId(USER_ID);
        reqVO.setUserDeleted(false);
        return reqVO;
    }

    @Test
    void noLevelExcludesSpecifiedLevelSpu() {
        PageResult<ProductBrowseHistoryDO> result = historyMapper.selectPage(req(), (Long) null);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
    }

    @Test
    void matchedLevelIncludesSpecifiedLevelSpu() {
        PageResult<ProductBrowseHistoryDO> result = historyMapper.selectPage(req(), 5L);
        assertEquals(3, result.getTotal());
        assertEquals(3, result.getList().size());
    }

    @Test
    void unmatchedLevelExcludesSpecifiedLevelSpu() {
        PageResult<ProductBrowseHistoryDO> result = historyMapper.selectPageExcludingEnterprise(req(), 6L);
        assertEquals(2, result.getTotal());
    }

}
