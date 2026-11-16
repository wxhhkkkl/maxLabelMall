package cn.iocoder.yudao.module.trade.service.cart;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.trade.controller.app.cart.vo.AppCartAddReqVO;
import cn.iocoder.yudao.module.trade.controller.app.cart.vo.AppCartListRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.cart.CartDO;
import cn.iocoder.yudao.module.trade.dal.mysql.cart.CartMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.PRODUCT_SPU_NOT_VISIBLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车的浏览等级过滤（019 / FR-529、FR-537、Q151）
 */
@ExtendWith(MockitoExtension.class)
public class CartBrowseLevelTest {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private ProductSpuApi productSpuApi;
    @Mock
    private MemberOrgApi memberOrgApi;
    @Mock
    private ProductSkuApi productSkuApi;
    @InjectMocks
    private CartServiceImpl cartService;

    private ProductSkuRespDTO sku(long id, long spuId) {
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(id);
        sku.setSpuId(spuId);
        sku.setStock(99);
        sku.setPrice(100);
        return sku;
    }

    private ProductSpuRespDTO spu(long id) {
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setId(id);
        spu.setStatus(ProductSpuStatusEnum.ENABLE.getStatus());
        spu.setStock(10);
        return spu;
    }

    private CartDO cart(long id, long spuId, long skuId) {
        CartDO cart = new CartDO();
        cart.setId(id);
        cart.setSpuId(spuId);
        cart.setSkuId(skuId);
        cart.setCount(1);
        cart.setSelected(true);
        return cart;
    }

    @Test
    void hiddenSpuCannotBeAddedToCart() {
        when(productSkuApi.getSku(301L)).thenReturn(success(sku(301L, 300L)));
        when(productSpuApi.getSpu(300L)).thenReturn(success(spu(300L)));
        when(productSpuApi.validateSpuVisible(1001L, 300L)).thenReturn(success(false));

        AppCartAddReqVO reqVO = new AppCartAddReqVO();
        reqVO.setSkuId(301L);
        reqVO.setCount(1);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> cartService.addCart(1001L, reqVO));
        assertEquals(PRODUCT_SPU_NOT_VISIBLE.getCode(), ex.getCode());
    }

    @Test
    void cartListHidesButDoesNotDeleteHiddenItem() {
        when(cartMapper.selectListByUserId(1001L)).thenReturn(new ArrayList<>(List.of(
                cart(1L, 300L, 301L), cart(2L, 200L, 201L))));
        when(productSpuApi.getSpuList(any())).thenReturn(success(List.of(spu(300L), spu(200L))));
        when(productSkuApi.getSkuList(any())).thenReturn(success(List.of(sku(301L, 300L), sku(201L, 200L))));
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(null));
        when(productSpuApi.getVisibleSpuIds(eq(1001L), any())).thenReturn(success(List.of(200L)));

        AppCartListRespVO result = cartService.getCartList(1001L);

        assertEquals(1, result.getValidList().size());
        assertEquals(200L, result.getValidList().get(0).getSpu().getId());
        verify(cartMapper, never()).deleteById(any(Long.class));
    }

}
