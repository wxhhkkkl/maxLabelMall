package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;

import java.util.Collection;
import java.util.List;

/**
 * 商品浏览等级 Service（特性 019）
 *
 * 唯一规则来源：公开 / 全部登录会员 / 指定等级会员。
 *
 * @author MaxLabel
 */
public interface ProductSpuBrowseLevelService {

    /**
     * 获得会员当前等级编号
     *
     * @param userId 会员编号；未登录传 null
     * @return 等级编号；未登录、会员不存在、无等级（levelId 为 null/0）时返回 null
     */
    Long getViewerLevelId(Long userId);

    /**
     * 判断当前会员是否可见该商品（内部解析等级）
     *
     * @param spu    商品
     * @param userId 会员编号；未登录传 null
     * @return 是否可见
     */
    boolean isVisible(ProductSpuDO spu, Long userId);

    /**
     * 判断会员是否可见该商品（使用已解析的等级，批量场景避免重复 RPC）
     *
     * @param spu     商品
     * @param userId  会员编号；未登录传 null
     * @param levelId 会员等级编号；无等级传 null
     * @return 是否可见
     */
    boolean isVisible(ProductSpuDO spu, Long userId, Long levelId);

    /**
     * 校验当前登录会员是否可见该商品
     *
     * @param spu 商品
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 不可见时抛 {@code SPU_NOT_EXISTS}
     */
    void validateVisible(ProductSpuDO spu);

    /**
     * 过滤出当前会员可见的商品编号
     *
     * @param userId 会员编号；未登录传 null
     * @param spuIds 商品编号集合
     * @return 可见的商品编号
     */
    List<Long> filterVisibleSpuIds(Long userId, Collection<Long> spuIds);

}
