package cn.iocoder.yudao.module.trade.service.cart;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.trade.controller.app.cart.vo.AppCartAddReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.cart.CartDO;
import cn.iocoder.yudao.module.trade.dal.mysql.cart.CartMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ENTERPRISE_PRODUCT_NOT_AVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 企业专享商品只能由企业 OWNER/ADMIN 加购（012 / R75）。
 */
@ExtendWith(MockitoExtension.class)
public class CartEnterpriseVisibilityTest {

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

    private ProductSkuRespDTO sku() {
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(301L);
        sku.setSpuId(300L);
        sku.setStock(99);
        sku.setPrice(100);
        return sku;
    }

    private ProductSpuRespDTO enterpriseSpu() {
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setId(300L);
        spu.setEnterpriseOnly(true);
        return spu;
    }

    private MemberOrgRespDTO org(int role, int status) {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setRole(role);
        org.setStatus(status);
        return org;
    }

    private AppCartAddReqVO request() {
        AppCartAddReqVO req = new AppCartAddReqVO();
        req.setSkuId(301L);
        req.setCount(1);
        return req;
    }

    @Test
    void nonAdminCannotAddEnterpriseSku() {
        when(productSkuApi.getSku(301L)).thenReturn(success(sku()));
        when(productSpuApi.getSpu(300L)).thenReturn(success(enterpriseSpu()));
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> cartService.addCart(1001L, request()));
        assertEquals(ENTERPRISE_PRODUCT_NOT_AVAILABLE.getCode(), exception.getCode());
    }

    @Test
    void ordinaryMemberCannotAddEnterpriseSku() {
        when(productSkuApi.getSku(301L)).thenReturn(success(sku()));
        when(productSpuApi.getSpu(300L)).thenReturn(success(enterpriseSpu()));
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(org(3, 0)));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> cartService.addCart(1001L, request()));
        assertEquals(ENTERPRISE_PRODUCT_NOT_AVAILABLE.getCode(), exception.getCode());
    }

    @Test
    void enterpriseAdminCanAddEnterpriseSku() {
        when(productSkuApi.getSku(301L)).thenReturn(success(sku()));
        when(productSpuApi.getSpu(300L)).thenReturn(success(enterpriseSpu()));
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(org(1, 0)));
        when(cartMapper.selectByUserIdAndSkuId(1001L, 301L)).thenReturn(null);
        when(cartMapper.insert(any(CartDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, CartDO.class).setId(9L);
            return 1;
        });

        assertEquals(9L, cartService.addCart(1001L, request()));
    }

}
