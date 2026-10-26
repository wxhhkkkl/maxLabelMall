package cn.iocoder.yudao.module.product.service.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanUpdateReqVO;
import cn.iocoder.yudao.module.product.controller.app.subscriptionplan.vo.AppMaxLabelSubscriptionPlanRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;

import java.util.List;

public interface MaxLabelSubscriptionPlanService {

    Long createPlan(MaxLabelSubscriptionPlanCreateReqVO reqVO);

    void updatePlan(MaxLabelSubscriptionPlanUpdateReqVO reqVO);

    MaxLabelSubscriptionPlanDO getPlan(Long id);

    MaxLabelSubscriptionPlanDO getPlanBySkuId(Long skuId);

    PageResult<MaxLabelSubscriptionPlanDO> getPlanPage(MaxLabelSubscriptionPlanPageReqVO reqVO);

    List<MaxLabelSubscriptionPlanDO> getEnabledPlanList();

    List<AppMaxLabelSubscriptionPlanRespVO> getEnabledPlanListWithProduct();

    List<AppMaxLabelSubscriptionPlanRespVO> getEnabledEnterprisePlanListWithProduct();

    AppMaxLabelSubscriptionPlanRespVO getEnterprisePlanDetail(Long skuId);

}
