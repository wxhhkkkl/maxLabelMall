package cn.iocoder.yudao.module.product.service.favorite;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.favorite.ProductFavoriteMapper;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuBrowseLevelService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 收藏入口不能绕过浏览等级（019 / FR-538）
 */
@ExtendWith(MockitoExtension.class)
public class ProductFavoriteBrowseLevelTest {

    @Mock
    private ProductFavoriteMapper productFavoriteMapper;
    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private MemberOrgApi memberOrgApi;
    @Mock
    private ProductSpuBrowseLevelService browseLevelService;
    @InjectMocks
    private ProductFavoriteServiceImpl favoriteService;

    @Test
    void hiddenSpuCannotBeFavorited() {
        ProductSpuDO spu = ProductSpuDO.builder()
                .id(3L).name("等级商品").enterpriseOnly(false).browseLevelType(2).build();
        when(productSpuService.getSpu(3L)).thenReturn(spu);
        when(browseLevelService.isVisible(spu, 1001L)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> favoriteService.createFavorite(1001L, 3L));
        assertEquals(SPU_NOT_EXISTS.getCode(), ex.getCode());
    }

}
