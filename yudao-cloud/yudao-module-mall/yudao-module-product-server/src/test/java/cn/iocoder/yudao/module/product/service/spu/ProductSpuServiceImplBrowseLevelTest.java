package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.api.level.MemberLevelApi;
import cn.iocoder.yudao.module.member.api.level.dto.MemberLevelRespDTO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.dal.mysql.subscriptionplan.MaxLabelSubscriptionPlanMapper;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.filter.ProductSpuFilterTagService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_BROWSE_LEVEL_IDS_EMPTY;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_BROWSE_LEVEL_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_BROWSE_LEVEL_TYPE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品浏览等级保存校验（019 / FR-516 ~ FR-520、FR-536、SC-198）
 */
@ExtendWith(MockitoExtension.class)
public class ProductSpuServiceImplBrowseLevelTest {

    @Mock
    private ProductSpuMapper productSpuMapper;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductBrandService brandService;
    @Mock
    private ProductCategoryService categoryService;
    @Mock
    private ProductSpuBrowseLevelService browseLevelService;
    @Mock
    private MemberLevelApi memberLevelApi;
    @Mock
    private ProductSpuFilterTagService spuFilterTagService;
    @Mock
    private MaxLabelSubscriptionPlanMapper subscriptionPlanMapper;

    @InjectMocks
    private ProductSpuServiceImpl productSpuService;

    private void mockLevel(Long id) {
        MemberLevelRespDTO level = new MemberLevelRespDTO();
        level.setId(id);
        when(memberLevelApi.getMemberLevel(id)).thenReturn(success(level));
    }

    // ========== 归一化规则 ==========

    @Test
    void nullTypeFallsBackToPublicAndClearsIds() {
        assertEquals(Collections.emptyList(), productSpuService.normalizeBrowseLevelIds(null, List.of(3L)));
    }

    @Test
    void nonLevelTypeClearsIds() {
        assertEquals(Collections.emptyList(), productSpuService.normalizeBrowseLevelIds(1, List.of(3L)));
    }

    @Test
    void invalidTypeIsRejected() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> productSpuService.normalizeBrowseLevelIds(99, null));
        assertEquals(SPU_BROWSE_LEVEL_TYPE_INVALID.getCode(), ex.getCode());
    }

    @Test
    void levelTypeRequiresAtLeastOneId() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> productSpuService.normalizeBrowseLevelIds(2, Collections.emptyList()));
        assertEquals(SPU_BROWSE_LEVEL_IDS_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void levelTypeDeduplicatesAndValidates() {
        mockLevel(3L);
        mockLevel(5L);
        assertEquals(List.of(3L, 5L),
                productSpuService.normalizeBrowseLevelIds(2, List.of(3L, 5L, 5L)));
    }

    @Test
    void levelTypeRejectsMissingLevel() {
        when(memberLevelApi.getMemberLevel(3L)).thenReturn(success(null));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> productSpuService.normalizeBrowseLevelIds(2, List.of(3L)));
        assertEquals(SPU_BROWSE_LEVEL_NOT_EXISTS.getCode(), ex.getCode());
    }

    // ========== 更新链路：浏览等级不改变状态 / 价格 / 库存（FR-536） ==========

    @Test
    void updateKeepsStatusPriceStock() {
        ProductSpuDO oldSpu = ProductSpuDO.builder()
                .id(1L).status(1).price(999).stock(999).enterpriseOnly(false).build();
        when(productSpuMapper.selectById(1L)).thenReturn(oldSpu);
        when(categoryService.getCategoryLevel(114L)).thenReturn(2);
        when(productSpuMapper.updateById(any(ProductSpuDO.class))).thenReturn(1);
        mockLevel(3L);
        mockLevel(5L);

        ProductSkuSaveReqVO sku = new ProductSkuSaveReqVO();
        sku.setPrice(100);
        sku.setMarketPrice(120);
        sku.setCostPrice(80);
        sku.setStock(10);
        ProductSpuSaveReqVO reqVO = new ProductSpuSaveReqVO();
        reqVO.setId(1L);
        reqVO.setName("商品");
        reqVO.setKeyword("关键字");
        reqVO.setIntroduction("简介");
        reqVO.setDescription("详情");
        reqVO.setCategoryId(114L);
        reqVO.setBrandId(1L);
        reqVO.setPicUrl("https://example.com/p.png");
        reqVO.setSliderPicUrls(List.of());
        reqVO.setSort(0);
        reqVO.setSpecType(false);
        reqVO.setDeliveryTypes(List.of(1));
        reqVO.setDeliveryTemplateId(1L);
        reqVO.setGiveIntegral(0);
        reqVO.setSubCommissionType(false);
        reqVO.setSkus(List.of(sku));
        reqVO.setBrowseLevelType(2);
        reqVO.setBrowseLevelIds(List.of(3L, 5L, 5L));

        productSpuService.updateSpu(reqVO);

        ArgumentCaptor<ProductSpuDO> captor = ArgumentCaptor.forClass(ProductSpuDO.class);
        verify(productSpuMapper).updateById(captor.capture());
        ProductSpuDO updated = captor.getValue();
        assertEquals(2, updated.getBrowseLevelType());
        assertEquals(List.of(3L, 5L), updated.getBrowseLevelIds());
        assertEquals(1, updated.getStatus());
        assertEquals(100, updated.getPrice());
        assertEquals(10, updated.getStock());
    }

}
