package cn.iocoder.yudao.module.product.service.favorite;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.favorite.ProductFavoriteDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.favorite.ProductFavoriteMapper;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FAVORITE_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_ENTERPRISE_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 企业专享商品的收藏入口（012 / R75）：非企业 OWNER/ADMIN 收藏必须被拒绝，
 * 否则收藏列表会成为绕过公开商城过滤的泄露通道。
 */
@ExtendWith(MockitoExtension.class)
public class ProductFavoriteServiceImplTest {

    @Mock
    private ProductFavoriteMapper productFavoriteMapper;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private MemberOrgApi memberOrgApi;
    @InjectMocks
    private ProductFavoriteServiceImpl favoriteService;

    private ProductSpuDO enterpriseSpu() {
        return ProductSpuDO.builder().id(300L).name("企业商品").enterpriseOnly(true).build();
    }

    private MemberOrgRespDTO org(int role) {
        MemberOrgRespDTO org = new MemberOrgRespDTO();
        org.setOrgId(88L);
        org.setRole(role);
        org.setStatus(0);
        return org;
    }

    @Test
    void nonAdminCannotFavoriteEnterpriseSpu() {
        when(productSpuService.getSpu(300L)).thenReturn(enterpriseSpu());
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> favoriteService.createFavorite(1001L, 300L));
        assertEquals(SPU_ENTERPRISE_ONLY.getCode(), exception.getCode());
    }

    @Test
    void ordinaryMemberCannotFavoriteEnterpriseSpu() {
        when(productSpuService.getSpu(300L)).thenReturn(enterpriseSpu());
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(org(3)));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> favoriteService.createFavorite(1001L, 300L));
        assertEquals(SPU_ENTERPRISE_ONLY.getCode(), exception.getCode());
    }

    @Test
    void publicSpuPassesVisibilityCheck() {
        ProductSpuDO publicSpu = ProductSpuDO.builder().id(200L).name("公开商品").enterpriseOnly(false).build();
        when(productSpuService.getSpu(200L)).thenReturn(publicSpu);
        // 已收藏 → 抛 FAVORITE_EXISTS，说明它已经越过了「企业专享」那道校验
        when(productFavoriteMapper.selectByUserIdAndSpuId(1001L, 200L))
                .thenReturn(ProductFavoriteDO.builder().id(1L).build());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> favoriteService.createFavorite(1001L, 200L));
        assertEquals(FAVORITE_EXISTS.getCode(), exception.getCode());
    }
}
