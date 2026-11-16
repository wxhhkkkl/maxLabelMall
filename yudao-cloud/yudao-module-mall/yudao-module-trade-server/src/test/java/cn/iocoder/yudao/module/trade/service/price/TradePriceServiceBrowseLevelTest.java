package cn.iocoder.yudao.module.trade.service.price;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.PRODUCT_SPU_NOT_VISIBLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 价格计算 / 结算的浏览等级校验（019 / FR-529、SC-199）
 */
@ExtendWith(MockitoExtension.class)
public class TradePriceServiceBrowseLevelTest {

    @Mock
    private ProductSkuApi productSkuApi;
    @Mock
    private ProductSpuApi productSpuApi;
    @InjectMocks
    private TradePriceServiceImpl tradePriceService;

    private ProductSkuRespDTO sku() {
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(2L);
        sku.setSpuId(1L);
        sku.setStock(10);
        return sku;
    }

    private ProductSpuRespDTO spu(long id) {
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setId(id);
        return spu;
    }

    @Test
    void orderPriceRejectsHiddenSpu() {
        when(productSkuApi.getSkuList(any())).thenReturn(success(List.of(sku())));
        when(productSpuApi.validateSpuList(any())).thenReturn(success(List.of(spu(1L))));
        when(productSpuApi.getVisibleSpuIds(eq(1001L), any())).thenReturn(success(List.of()));

        TradePriceCalculateReqBO reqBO = new TradePriceCalculateReqBO();
        reqBO.setUserId(1001L);
        reqBO.setPointStatus(false);
        TradePriceCalculateReqBO.Item item = new TradePriceCalculateReqBO.Item();
        item.setSkuId(2L);
        item.setCount(1);
        reqBO.setItems(List.of(item));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> tradePriceService.calculateOrderPrice(reqBO));
        assertEquals(PRODUCT_SPU_NOT_VISIBLE.getCode(), ex.getCode());
        // 批量判定：不再逐个 SPU 调 validateSpuVisible
        verify(productSpuApi, never()).validateSpuVisible(any(), any());
    }

    @Test
    void productPriceFiltersHiddenSpu() {
        when(productSpuApi.getSpuList(any())).thenReturn(success(List.of(spu(1L), spu(2L))));
        when(productSpuApi.getVisibleSpuIds(eq(1001L), any())).thenReturn(success(List.of()));

        assertTrue(tradePriceService.calculateProductPrice(1001L, List.of(1L, 2L)).isEmpty());
    }

}
