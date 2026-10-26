package cn.iocoder.yudao.module.product.controller.app.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.product.controller.app.favorite.vo.AppFavoritePageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.favorite.ProductFavoriteDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.favorite.ProductFavoriteService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 非企业管理员的收藏分页走排除企业专享商品的查询（012 / R75 / T089）。
 */
@ExtendWith(MockitoExtension.class)
public class AppFavoriteVisibilityTest {

    @Mock
    private ProductFavoriteService productFavoriteService;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private MemberOrgApi memberOrgApi;
    @InjectMocks
    private AppFavoriteController controller;

    private ProductSpuDO publicSpu(long id) {
        return ProductSpuDO.builder()
                .id(id).name("公开商品").picUrl("").price(100).stock(10)
                .salesCount(0).virtualSalesCount(0)
                .build();
    }

    @Test
    void nonAdminUsesFilteredFavoritePage() {
        AppFavoritePageReqVO reqVO = new AppFavoritePageReqVO();
        ProductFavoriteDO favorite = ProductFavoriteDO.builder()
                .id(1L).userId(1001L).spuId(1L).build();
        when(productFavoriteService.getFavoritePageExcludingEnterprise(eq(1001L), any()))
                .thenReturn(new PageResult<>(List.of(favorite), 1L));
        when(productSpuService.getSpuList(any())).thenReturn(List.of(publicSpu(1L)));
        when(memberOrgApi.getByMemberId(1001L)).thenReturn(success(null));

        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            var page = controller.getFavoritePage(reqVO).getData();
            assertEquals(1L, page.getTotal());
            assertEquals(1, page.getList().size());
        }
        verify(productFavoriteService, never()).getFavoritePage(any(), any());
    }

}
