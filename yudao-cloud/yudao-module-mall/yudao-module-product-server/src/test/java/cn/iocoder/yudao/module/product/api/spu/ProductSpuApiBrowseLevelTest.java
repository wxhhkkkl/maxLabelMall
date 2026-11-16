package cn.iocoder.yudao.module.product.api.spu;

import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuBrowseLevelService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 商品浏览等级 RPC（019）：交易 / 营销模块通过它复用同一判定。
 */
@ExtendWith(MockitoExtension.class)
public class ProductSpuApiBrowseLevelTest {

    @Mock
    private ProductSpuService spuService;
    @Mock
    private ProductSpuBrowseLevelService browseLevelService;

    @InjectMocks
    private ProductSpuApiImpl productSpuApi;

    private ProductSpuDO spu(long id) {
        return ProductSpuDO.builder().id(id).name("商品" + id).build();
    }

    @Test
    void getVisibleSpuIdsReturnsOnlyVisible() {
        when(browseLevelService.filterVisibleSpuIds(1001L, List.of(1L, 2L)))
                .thenReturn(List.of(1L));

        assertEquals(List.of(1L), productSpuApi.getVisibleSpuIds(1001L, List.of(1L, 2L)).getData());
    }

    @Test
    void getVisibleSpuIdsReturnsEmptyForEmptyInput() {
        when(browseLevelService.filterVisibleSpuIds(1001L, List.of())).thenReturn(List.of());
        assertTrue(productSpuApi.getVisibleSpuIds(1001L, List.of()).getData().isEmpty());
    }

    @Test
    void validateSpuVisibleReturnsFalseWhenSpuNotExists() {
        when(spuService.getSpu(1L)).thenReturn(null);
        assertFalse(productSpuApi.validateSpuVisible(1001L, 1L).getData());
    }

    @Test
    void validateSpuVisibleDelegatesToBrowseLevel() {
        ProductSpuDO spu = spu(1L);
        when(spuService.getSpu(1L)).thenReturn(spu);
        when(browseLevelService.isVisible(spu, 1001L)).thenReturn(false);
        assertFalse(productSpuApi.validateSpuVisible(1001L, 1L).getData());
    }

}
