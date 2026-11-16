package cn.iocoder.yudao.module.product.dal.mysql.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.module.product.controller.admin.favorite.vo.ProductFavoritePageReqVO;
import cn.iocoder.yudao.module.product.controller.app.favorite.vo.AppFavoritePageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.favorite.ProductFavoriteDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductFavoriteMapper extends BaseMapperX<ProductFavoriteDO> {

    default ProductFavoriteDO selectByUserIdAndSpuId(Long userId, Long spuId) {
        return selectOne(ProductFavoriteDO::getUserId, userId,
                ProductFavoriteDO::getSpuId, spuId);
    }

    default PageResult<ProductFavoriteDO> selectPageByUserAndType(Long userId, Long levelId,
                                                                  AppFavoritePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapper<ProductFavoriteDO>()
                .eq(ProductFavoriteDO::getUserId, userId)
                .exists("SELECT 1 FROM product_spu ps WHERE ps.id = product_favorite.spu_id"
                        + " AND ps.deleted = 0 AND " + browseLevelCondition(), levelId)
                .orderByDesc(ProductFavoriteDO::getId));
    }

    /**
     * 非企业管理员：分页时直接排除企业专享 SPU，保证 total 与列表一致（012 / R75）
     */
    default PageResult<ProductFavoriteDO> selectPageByUserAndTypeExcludingEnterprise(
            Long userId, Long levelId, AppFavoritePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductFavoriteDO>()
                .eq(ProductFavoriteDO::getUserId, userId)
                .exists("SELECT 1 FROM product_spu ps WHERE ps.id = product_favorite.spu_id"
                        + " AND ps.deleted = 0"
                        + " AND (ps.enterprise_only = 0 OR ps.enterprise_only IS NULL)"
                        + " AND " + browseLevelCondition(), levelId)
                .orderByDesc(ProductFavoriteDO::getId));
    }

    /**
     * 浏览等级（019）：公开 / 全部登录会员（收藏必然是登录态）/ 指定等级
     *
     * levelId 为 null 时，find_in_set 结果为 NULL → 指定等级商品被排除。
     */
    static String browseLevelCondition() {
        return "(ps.browse_level_type = 0 OR ps.browse_level_type = 1"
                + " OR (ps.browse_level_type = 2 AND "
                + MyBatisUtils.findInSet("ps.browse_level_ids") + "))";
    }

    default PageResult<ProductFavoriteDO> selectPageByUserId(ProductFavoritePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductFavoriteDO>()
                .eqIfPresent(ProductFavoriteDO::getUserId, reqVO.getUserId())
                .orderByDesc(ProductFavoriteDO::getId));
    }

    default Long selectCountByUserId(Long userId) {
        return selectCount(ProductFavoriteDO::getUserId, userId);
    }

}
