package cn.iocoder.yudao.module.promotion.enums.banner;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BannerPositionEnum} 的取值契约测试。
 *
 * 为什么要为一个枚举专门写测试：**「商城页」这个位置是本项目新加的**，而后端对 banner
 * 位置做 {@code @InEnum(BannerPositionEnum.class)} **强校验**（见 {@code BannerBaseVO}）。
 * 校验用的就是这里的 {@code ARRAYS} —— 枚举里没这个值，管理端提交会被 400 拒绝；
 * **光往字典 `promotion_banner_position` 里加一行是没有用的**，两处必须同时有。
 *
 * 放在 promotion-server 而不是 promotion-api：枚举本身在 api 模块，但 api 模块没有测试依赖，
 * 而 server 模块有（且依赖 api）。为了一个枚举给 api 模块加测试依赖不划算。
 */
class BannerPositionEnumTest {

    @Test
    void testMallPositionExists() {
        assertEquals(6, BannerPositionEnum.MALL_POSITION.getPosition().intValue());
        assertEquals("商城页", BannerPositionEnum.MALL_POSITION.getName());
    }

    @Test
    void testMallPositionIsAcceptedByInEnum() {
        // @InEnum 靠 ARRAYS 校验；漏了它，新位置一样提交不上去
        assertTrue(
                Arrays.asList(BannerPositionEnum.ARRAYS).contains(6),
                "ARRAYS 必须包含商城页的位置值 6，否则 @InEnum 会拒绝它");
    }

    @Test
    void testPositionsAreUnique() {
        long distinct = Arrays.stream(BannerPositionEnum.ARRAYS).distinct().count();
        assertEquals(BannerPositionEnum.values().length, (int) distinct, "位置值不得重复");
    }

    @Test
    void testExistingPositionsUnchanged() {
        // 既有取值不能被改动 —— 线上已有数据按这些值存着
        assertEquals(1, BannerPositionEnum.HOME_POSITION.getPosition().intValue());
        assertEquals(2, BannerPositionEnum.SECKILL_POSITION.getPosition().intValue());
        assertEquals(3, BannerPositionEnum.COMBINATION_POSITION.getPosition().intValue());
        assertEquals(4, BannerPositionEnum.DISCOUNT_POSITION.getPosition().intValue());
        assertEquals(5, BannerPositionEnum.REWARD_POSITION.getPosition().intValue());
    }

}
