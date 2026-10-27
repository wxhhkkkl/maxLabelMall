package cn.iocoder.yudao.module.product.api.subscriptionplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.api.subscriptionplan.dto.ProductSubscriptionPlanRespDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.subscriptionplan.MaxLabelSubscriptionPlanDO;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * MaxLabel 会员授权商品 API 实现类
 */
@RestController
@Validated
public class ProductSubscriptionPlanApiImpl implements ProductSubscriptionPlanApi {

    @Resource
    private MaxLabelSubscriptionPlanService planService;

    @Override
    public CommonResult<ProductSubscriptionPlanRespDTO> getBySkuId(Long skuId) {
        MaxLabelSubscriptionPlanDO plan = planService.getPlanBySkuId(skuId);
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            return success(null);
        }
        return success(BeanUtils.toBean(plan, ProductSubscriptionPlanRespDTO.class));
    }

    @Override
    public CommonResult<List<Long>> getPlanSpuIds(Collection<Long> spuIds) {
        return success(planService.getPlanSpuIds(spuIds));
    }

}
