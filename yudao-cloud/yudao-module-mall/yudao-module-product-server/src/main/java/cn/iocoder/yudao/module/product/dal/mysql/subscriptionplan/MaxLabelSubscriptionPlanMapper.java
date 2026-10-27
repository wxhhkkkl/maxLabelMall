package cn.iocoder.yudao.module.product.dal.mysql.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MaxLabelSubscriptionPlanMapper extends BaseMapperX<MaxLabelSubscriptionPlanDO> {

    default MaxLabelSubscriptionPlanDO selectBySkuId(Long skuId) {
        return selectOne(MaxLabelSubscriptionPlanDO::getSkuId, skuId);
    }

    default List<MaxLabelSubscriptionPlanDO> selectListBySpuId(Long spuId) {
        return selectList(MaxLabelSubscriptionPlanDO::getSpuId, spuId);
    }

    /**
     * 给定 SPU 中，被会员授权计划引用过的那些 SPU 编号（口径与公开商城列表的 NOT EXISTS 一致）。
     */
    default List<Long> selectSpuIdsBySpuIds(Collection<Long> spuIds) {
        return selectList(new LambdaQueryWrapperX<MaxLabelSubscriptionPlanDO>()
                .in(MaxLabelSubscriptionPlanDO::getSpuId, spuIds))
                .stream().map(MaxLabelSubscriptionPlanDO::getSpuId).distinct().toList();
    }

    default List<MaxLabelSubscriptionPlanDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<MaxLabelSubscriptionPlanDO>()
                .eq(MaxLabelSubscriptionPlanDO::getEnabled, true)
                .orderByAsc(MaxLabelSubscriptionPlanDO::getSort)
                .orderByAsc(MaxLabelSubscriptionPlanDO::getId));
    }

    default PageResult<MaxLabelSubscriptionPlanDO> selectPage(MaxLabelSubscriptionPlanPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MaxLabelSubscriptionPlanDO>()
                .eqIfPresent(MaxLabelSubscriptionPlanDO::getSpuId, reqVO.getSpuId())
                .eqIfPresent(MaxLabelSubscriptionPlanDO::getScope, reqVO.getScope())
                .eqIfPresent(MaxLabelSubscriptionPlanDO::getEnabled, reqVO.getEnabled())
                .orderByAsc(MaxLabelSubscriptionPlanDO::getSort)
                .orderByDesc(MaxLabelSubscriptionPlanDO::getId));
    }

}
