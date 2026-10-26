package cn.iocoder.yudao.module.product.dal.mysql.spu;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 公开商品列表必须排除企业专享商品（012 / Q72）。
 *
 * 直接在 Mapper 层测：过滤条件就组装在这里，Controller 只是把分类展开。
 */
public class ProductSpuVisibilityTest extends BaseDbUnitTest {

    @Resource
    private ProductSpuMapper productSpuMapper;

    private Long publicSpuId;
    private Long enterpriseSpuId;

    @BeforeEach
    void prepareData() {
        publicSpuId = insertSpu("公开商品", false);
        enterpriseSpuId = insertSpu("企业商品", true);
    }

    private Long insertSpu(String name, boolean enterpriseOnly) {
        ProductSpuDO spu = ProductSpuDO.builder()
                .name(name).keyword("").introduction("").description("")
                .categoryId(114L).picUrl("").sort(0)
                .status(ProductSpuStatusEnum.ENABLE.getStatus())
                .specType(false).price(100).marketPrice(100).costPrice(100).stock(10)
                .deliveryTemplateId(1L).giveIntegral(0).subCommissionType(false)
                .enterpriseOnly(enterpriseOnly)
                .build();
        productSpuMapper.insert(spu);
        return spu.getId();
    }

    @Test
    void publicPageExcludesEnterpriseOnlySpu() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        PageResult<ProductSpuDO> result = productSpuMapper.selectPage(reqVO, Set.of());

        assertEquals(1, result.getTotal());
        assertEquals(publicSpuId, result.getList().get(0).getId());
    }

    @Test
    void publicSearchAlsoExcludesEnterpriseOnlySpu() {
        AppProductSpuPageReqVO reqVO = new AppProductSpuPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        reqVO.setKeyword("商品"); // 两个商品名都匹配关键字
        PageResult<ProductSpuDO> result = productSpuMapper.selectPage(reqVO, Set.of());

        assertEquals(1, result.getTotal());
        assertEquals(publicSpuId, result.getList().get(0).getId());
        assertEquals("公开商品", result.getList().get(0).getName());
    }

}
