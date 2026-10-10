package cn.iocoder.yudao.module.promotion.enums.banner;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Banner Position 枚举
 *
 * @author HUIHUI
 */
@AllArgsConstructor
@Getter
public enum BannerPositionEnum implements ArrayValuable<Integer> {

    HOME_POSITION(1, "首页"),
    SECKILL_POSITION(2, "秒杀活动页"),
    COMBINATION_POSITION(3, "砍价活动页"),
    DISCOUNT_POSITION(4, "限时折扣页"),
    REWARD_POSITION(5, "满减送页"),
    /**
     * 商城页（商品列表页顶部的横幅）。
     *
     * ⚠️ **本项目新增**（2026-10-10），上游没有这个位置。加它的原因是：
     * 既没有哪个取值语义贴近"商品列表页"，而复用「首页」会让两处被迫共用同一批内容。
     *
     * ⚠️ 与数据库字典 `promotion_banner_position` 里的同名取值**必须成对存在** ——
     * 只有字典没有它 → 管理端提交被 `@InEnum` 400 拒绝；只有它没有字典 → 运营在后台选不到。
     */
    MALL_POSITION(6, "商城页");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(BannerPositionEnum::getPosition).toArray(Integer[]::new);

    /**
     * 值
     */
    private final Integer position;
    /**
     * 名字
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
