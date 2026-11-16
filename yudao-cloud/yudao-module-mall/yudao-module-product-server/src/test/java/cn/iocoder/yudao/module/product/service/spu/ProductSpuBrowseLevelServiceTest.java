package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 商品浏览等级判定矩阵（019 / FR-522 ~ FR-524、FR-533）
 */
@ExtendWith(MockitoExtension.class)
public class ProductSpuBrowseLevelServiceTest {

    @Mock
    private MemberUserApi memberUserApi;
    @Mock
    private ProductSpuMapper productSpuMapper;

    @InjectMocks
    private ProductSpuBrowseLevelServiceImpl browseLevelService;

    private ProductSpuDO spu(Integer type, Long... levelIds) {
        return ProductSpuDO.builder()
                .id(1L).name("测试商品")
                .browseLevelType(type)
                .browseLevelIds(levelIds == null ? null : List.of(levelIds))
                .build();
    }

    private void mockLevel(Long userId, Long levelId) {
        MemberUserRespDTO user = new MemberUserRespDTO();
        user.setId(userId);
        user.setLevelId(levelId);
        when(memberUserApi.getUser(userId)).thenReturn(success(user));
    }

    // ========== getViewerLevelId ==========

    @Test
    void guestHasNoLevel() {
        assertNull(browseLevelService.getViewerLevelId(null));
    }

    @Test
    void levelZeroMeansNoLevel() {
        mockLevel(1001L, 0L);
        assertNull(browseLevelService.getViewerLevelId(1001L));
    }

    @Test
    void levelIdIsReturned() {
        mockLevel(1001L, 5L);
        assertEquals(5L, browseLevelService.getViewerLevelId(1001L));
    }

    // ========== 判定矩阵 ==========

    @Test
    void publicSpuVisibleToGuestAndMembers() {
        ProductSpuDO spu = spu(0);
        assertTrue(browseLevelService.isVisible(spu, null));
        assertTrue(browseLevelService.isVisible(spu, 1001L, null));
        assertTrue(browseLevelService.isVisible(spu, 1001L, 5L));
    }

    @Test
    void loginOnlySpuHiddenFromGuest() {
        ProductSpuDO spu = spu(1);
        assertFalse(browseLevelService.isVisible(spu, null));
        assertTrue(browseLevelService.isVisible(spu, 1001L, null));
        assertTrue(browseLevelService.isVisible(spu, 1001L, 5L));
    }

    @Test
    void levelSpuRequiresMatchedLevel() {
        ProductSpuDO spu = spu(2, 5L, 6L);
        assertFalse(browseLevelService.isVisible(spu, null));
        assertFalse(browseLevelService.isVisible(spu, 1001L, null)); // 无等级
        assertTrue(browseLevelService.isVisible(spu, 1001L, 5L));
        assertTrue(browseLevelService.isVisible(spu, 1001L, 6L));
        assertFalse(browseLevelService.isVisible(spu, 1001L, 7L));
    }

    @Test
    void levelSpuWithEmptyIdsIsInvisibleToEveryone() {
        ProductSpuDO spu = spu(2);
        assertFalse(browseLevelService.isVisible(spu, 1001L, 5L));
    }

    @Test
    void unknownTypeIsFailClosed() {
        assertFalse(browseLevelService.isVisible(spu(99), 1001L, 5L));
        assertFalse(browseLevelService.isVisible(spu(null), 1001L, 5L));
    }

    @Test
    void enterpriseAdminIsNotExemptFromLevelCheck() {
        // 服务不读取企业身份；等级不匹配时，任何已登录会员都不可见
        ProductSpuDO spu = spu(2, 5L);
        assertFalse(browseLevelService.isVisible(spu, 1001L, 6L));
    }

    // ========== 批量过滤 ==========

    @Test
    void filterVisibleSpuIdsKeepsOnlyVisibleOnes() {
        ProductSpuDO publicSpu = spu(0).setId(1L);
        ProductSpuDO loginSpu = spu(1).setId(2L);
        ProductSpuDO levelSpu = spu(2, 5L).setId(3L);
        when(productSpuMapper.selectByIds(any())).thenReturn(List.of(publicSpu, loginSpu, levelSpu));
        mockLevel(1001L, 6L);

        assertEquals(List.of(1L, 2L),
                browseLevelService.filterVisibleSpuIds(1001L, List.of(1L, 2L, 3L)));
    }

}
