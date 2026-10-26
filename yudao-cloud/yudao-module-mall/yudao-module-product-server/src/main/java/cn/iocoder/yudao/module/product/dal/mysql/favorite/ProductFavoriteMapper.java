package cn.iocoder.yudao.module.product.dal.mysql.favorite;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
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

    default PageResult<ProductFavoriteDO> selectPageByUserAndType(Long userId, AppFavoritePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapper<ProductFavoriteDO>()
                .eq(ProductFavoriteDO::getUserId, userId)
                .orderByDesc(ProductFavoriteDO::getId));
    }

    /**
     * 非企业管理员：分页时直接排除企业专享 SPU，保证 total 与列表一致（012 / R75）
     */
    default PageResult<ProductFavoriteDO> selectPageByUserAndTypeExcludingEnterprise(
            Long userId, AppFavoritePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductFavoriteDO>()
                .eq(ProductFavoriteDO::getUserId, userId)
                .exists("SELECT 1 FROM product_spu ps WHERE ps.id = product_favorite.spu_id"
                        + " AND ps.deleted = 0"
                        + " AND (ps.enterprise_only = 0 OR ps.enterprise_only IS NULL)")
                .orderByDesc(ProductFavoriteDO::getId));
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
