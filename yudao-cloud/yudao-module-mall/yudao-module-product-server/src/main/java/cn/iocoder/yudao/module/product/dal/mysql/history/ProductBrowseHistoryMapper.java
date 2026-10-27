package cn.iocoder.yudao.module.product.dal.mysql.history;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.history.vo.ProductBrowseHistoryPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.history.ProductBrowseHistoryDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;

/**
 * 商品浏览记录 Mapper
 *
 * @author owen
 */
@Mapper
public interface ProductBrowseHistoryMapper extends BaseMapperX<ProductBrowseHistoryDO> {

    default ProductBrowseHistoryDO selectByUserIdAndSpuId(Long userId, Long spuId) {
        return selectFirstOne(ProductBrowseHistoryDO::getUserId, userId,
                ProductBrowseHistoryDO::getSpuId, spuId);
    }

    default PageResult<ProductBrowseHistoryDO> selectPage(ProductBrowseHistoryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductBrowseHistoryDO>()
                .eqIfPresent(ProductBrowseHistoryDO::getUserId, reqVO.getUserId())
                .eqIfPresent(ProductBrowseHistoryDO::getUserDeleted, reqVO.getUserDeleted())
                .eqIfPresent(ProductBrowseHistoryDO::getSpuId, reqVO.getSpuId())
                .betweenIfPresent(ProductBrowseHistoryDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ProductBrowseHistoryDO::getId));
    }

    /**
     * 非企业管理员：分页时直接排除企业专享 SPU，保证 total 与列表一致（012 / R75）。
     * 写法与 {@code ProductFavoriteMapper#selectPageByUserAndTypeExcludingEnterprise} 保持一致。
     */
    default PageResult<ProductBrowseHistoryDO> selectPageExcludingEnterprise(ProductBrowseHistoryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductBrowseHistoryDO>()
                .eqIfPresent(ProductBrowseHistoryDO::getUserId, reqVO.getUserId())
                .eqIfPresent(ProductBrowseHistoryDO::getUserDeleted, reqVO.getUserDeleted())
                .eqIfPresent(ProductBrowseHistoryDO::getSpuId, reqVO.getSpuId())
                .betweenIfPresent(ProductBrowseHistoryDO::getCreateTime, reqVO.getCreateTime())
                .exists("SELECT 1 FROM product_spu ps WHERE ps.id = product_browse_history.spu_id"
                        + " AND ps.deleted = 0"
                        + " AND (ps.enterprise_only = 0 OR ps.enterprise_only IS NULL)")
                .orderByDesc(ProductBrowseHistoryDO::getId));
    }

    default void updateUserDeletedByUserId(Long userId, Collection<Long> spuIds, Boolean userDeleted) {
        update(new LambdaUpdateWrapper<ProductBrowseHistoryDO>()
                .eq(ProductBrowseHistoryDO::getUserId, userId)
                .in(CollUtil.isNotEmpty(spuIds), ProductBrowseHistoryDO::getSpuId, spuIds)
                .set(ProductBrowseHistoryDO::getUserDeleted, userDeleted));
    }

    default Page<ProductBrowseHistoryDO> selectPageByUserIdOrderByCreateTimeAsc(Long userId, Integer pageNo, Integer pageSize) {
        Page<ProductBrowseHistoryDO> page = Page.of(pageNo, pageSize);
        return selectPage(page, new LambdaQueryWrapperX<ProductBrowseHistoryDO>()
                .eqIfPresent(ProductBrowseHistoryDO::getUserId, userId)
                .orderByAsc(ProductBrowseHistoryDO::getCreateTime));
    }

}