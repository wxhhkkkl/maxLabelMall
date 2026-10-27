package cn.iocoder.yudao.module.product.service.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanUpdateReqVO;
import cn.iocoder.yudao.module.product.controller.app.subscriptionplan.vo.AppMaxLabelSubscriptionPlanRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;

import java.util.Collection;
import java.util.List;

public interface MaxLabelSubscriptionPlanService {

    Long createPlan(MaxLabelSubscriptionPlanCreateReqVO reqVO);

    void updatePlan(MaxLabelSubscriptionPlanUpdateReqVO reqVO);

    MaxLabelSubscriptionPlanDO getPlan(Long id);

    MaxLabelSubscriptionPlanDO getPlanBySkuId(Long skuId);

    PageResult<MaxLabelSubscriptionPlanDO> getPlanPage(MaxLabelSubscriptionPlanPageReqVO reqVO);

    List<MaxLabelSubscriptionPlanDO> getEnabledPlanList();

    /**
     * 给定 SPU 中，被会员授权计划引用过的那些 SPU 编号（不进入公开商城，FR-328）。
     */
    List<Long> getPlanSpuIds(Collection<Long> spuIds);

    List<AppMaxLabelSubscriptionPlanRespVO> getEnabledPlanListWithProduct();

    List<AppMaxLabelSubscriptionPlanRespVO> getEnabledEnterprisePlanListWithProduct();

    AppMaxLabelSubscriptionPlanRespVO getEnterprisePlanDetail(Long skuId);

}
