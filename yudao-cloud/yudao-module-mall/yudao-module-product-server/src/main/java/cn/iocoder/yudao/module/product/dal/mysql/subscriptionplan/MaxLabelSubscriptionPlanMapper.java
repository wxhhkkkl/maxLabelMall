package cn.iocoder.yudao.module.product.dal.mysql.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MaxLabelSubscriptionPlanMapper extends BaseMapperX<MaxLabelSubscriptionPlanDO> {

    default MaxLabelSubscriptionPlanDO selectBySkuId(Long skuId) {
        return selectOne(MaxLabelSubscriptionPlanDO::getSkuId, skuId);
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
                .eqIfPresent(MaxLabelSubscriptionPlanDO::getEnabled, reqVO.getEnabled())
                .orderByAsc(MaxLabelSubscriptionPlanDO::getSort)
                .orderByDesc(MaxLabelSubscriptionPlanDO::getId));
    }

}
