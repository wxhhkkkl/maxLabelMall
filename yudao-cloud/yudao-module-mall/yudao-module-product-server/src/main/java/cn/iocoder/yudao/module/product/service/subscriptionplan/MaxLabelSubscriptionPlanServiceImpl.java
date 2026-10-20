package cn.iocoder.yudao.module.product.service.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.subscriptionplan.vo.MaxLabelSubscriptionPlanUpdateReqVO;
import cn.iocoder.yudao.module.product.controller.app.subscriptionplan.vo.AppMaxLabelSubscriptionPlanRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;
import cn.iocoder.yudao.module.product.dal.mysql.subscriptionplan.MaxLabelSubscriptionPlanMapper;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

/**
 * MaxLabel 会员授权商品映射 Service 实现类。
 *
 * 校验绑定在商品模块内完成：SKU 归属、SPU 仅虚拟商品、价格 > 0、单一启用 SPU。
 */
@Service
@Validated
public class MaxLabelSubscriptionPlanServiceImpl implements MaxLabelSubscriptionPlanService {

    /** 虚拟商品配送方式，对应 DeliveryTypeEnum.VIRTUAL */
    private static final int DELIVERY_TYPE_VIRTUAL = 3;

    @Resource
    private MaxLabelSubscriptionPlanMapper planMapper;
    @Resource
    private ProductSpuService spuService;
    @Resource
    private ProductSkuService skuService;

    @Override
    public Long createPlan(MaxLabelSubscriptionPlanCreateReqVO reqVO) {
        validateSku(reqVO.getSpuId(), reqVO.getSkuId());
        validateUnique(null, reqVO.getSkuId());
        if (Boolean.TRUE.equals(reqVO.getEnabled())) {
            validateSingleSpu(reqVO.getSpuId(), null);
        }
        MaxLabelSubscriptionPlanDO plan = MaxLabelSubscriptionPlanDO.builder()
                .spuId(reqVO.getSpuId())
                .skuId(reqVO.getSkuId())
                .periodMonths(reqVO.getPeriodMonths())
                .enabled(Boolean.TRUE.equals(reqVO.getEnabled()))
                .sort(reqVO.getSort() == null ? 0 : reqVO.getSort())
                .build();
        planMapper.insert(plan);
        return plan.getId();
    }

    @Override
    public void updatePlan(MaxLabelSubscriptionPlanUpdateReqVO reqVO) {
        MaxLabelSubscriptionPlanDO plan = validatePlanExists(reqVO.getId());
        if (Boolean.TRUE.equals(reqVO.getEnabled())) {
            validateSingleSpu(plan.getSpuId(), plan.getId());
        }
        plan.setPeriodMonths(reqVO.getPeriodMonths());
        plan.setEnabled(Boolean.TRUE.equals(reqVO.getEnabled()));
        plan.setSort(reqVO.getSort() == null ? 0 : reqVO.getSort());
        planMapper.updateById(plan);
    }

    @Override
    public MaxLabelSubscriptionPlanDO getPlan(Long id) {
        return planMapper.selectById(id);
    }

    @Override
    public MaxLabelSubscriptionPlanDO getPlanBySkuId(Long skuId) {
        return planMapper.selectBySkuId(skuId);
    }

    @Override
    public PageResult<MaxLabelSubscriptionPlanDO> getPlanPage(MaxLabelSubscriptionPlanPageReqVO reqVO) {
        return planMapper.selectPage(reqVO);
    }

    @Override
    public List<MaxLabelSubscriptionPlanDO> getEnabledPlanList() {
        return planMapper.selectEnabledList();
    }

    @Override
    public List<AppMaxLabelSubscriptionPlanRespVO> getEnabledPlanListWithProduct() {
        List<MaxLabelSubscriptionPlanDO> plans = planMapper.selectEnabledList();
        List<AppMaxLabelSubscriptionPlanRespVO> result = new ArrayList<>(plans.size());
        for (MaxLabelSubscriptionPlanDO plan : plans) {
            ProductSpuDO spu = spuService.getSpu(plan.getSpuId());
            ProductSkuDO sku = skuService.getSku(plan.getSkuId());
            if (spu == null || sku == null) {
                continue;
            }
            AppMaxLabelSubscriptionPlanRespVO vo = new AppMaxLabelSubscriptionPlanRespVO();
            vo.setSpuId(plan.getSpuId());
            vo.setSkuId(plan.getSkuId());
            vo.setName(spu.getName());
            vo.setPeriodMonths(plan.getPeriodMonths());
            vo.setPrice(sku.getPrice());
            vo.setPicUrl(sku.getPicUrl() != null ? sku.getPicUrl() : spu.getPicUrl());
            vo.setStock(sku.getStock());
            vo.setSort(plan.getSort());
            result.add(vo);
        }
        return result;
    }

    private MaxLabelSubscriptionPlanDO validatePlanExists(Long id) {
        MaxLabelSubscriptionPlanDO plan = planMapper.selectById(id);
        if (plan == null) {
            throw exception(MEMBER_SUBSCRIPTION_PLAN_NOT_EXISTS);
        }
        return plan;
    }

    private void validateSku(Long spuId, Long skuId) {
        ProductSkuDO sku = skuService.getSku(skuId);
        if (sku == null || !Objects.equals(sku.getSpuId(), spuId)) {
            throw exception(SKU_NOT_EXISTS);
        }
        ProductSpuDO spu = spuService.getSpu(spuId);
        if (spu == null) {
            throw exception(SPU_NOT_EXISTS);
        }
        List<Integer> deliveryTypes = spu.getDeliveryTypes();
        if (deliveryTypes == null || deliveryTypes.isEmpty()
                || deliveryTypes.stream().anyMatch(type -> !Objects.equals(type, DELIVERY_TYPE_VIRTUAL))) {
            throw exception(MEMBER_SUBSCRIPTION_PLAN_NOT_VIRTUAL);
        }
        if (sku.getPrice() == null || sku.getPrice() <= 0) {
            throw exception(MEMBER_SUBSCRIPTION_PLAN_PRICE_ZERO);
        }
    }

    private void validateUnique(Long id, Long skuId) {
        MaxLabelSubscriptionPlanDO exists = planMapper.selectBySkuId(skuId);
        if (exists != null && !Objects.equals(exists.getId(), id)) {
            throw exception(MEMBER_SUBSCRIPTION_PLAN_DUPLICATE);
        }
    }

    private void validateSingleSpu(Long spuId, Long excludeId) {
        boolean conflict = planMapper.selectEnabledList().stream()
                .anyMatch(plan -> !Objects.equals(plan.getId(), excludeId)
                        && !Objects.equals(plan.getSpuId(), spuId));
        if (conflict) {
            throw exception(MEMBER_SUBSCRIPTION_PLAN_MULTI_SPU);
        }
    }

}
