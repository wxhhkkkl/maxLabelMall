package cn.iocoder.yudao.module.promotion.controller.admin.seckill;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.product.api.subscriptionplan.ProductSubscriptionPlanApi;
import cn.iocoder.yudao.module.promotion.controller.admin.seckill.vo.activity.*;
import cn.iocoder.yudao.module.promotion.convert.seckill.SeckillActivityConvert;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillProductDO;
import cn.iocoder.yudao.module.promotion.service.seckill.SeckillActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_ENTERPRISE_ONLY;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_SUBSCRIPTION_ONLY;

@Tag(name = "管理后台 - 秒杀活动")
@RestController
@RequestMapping("/promotion/seckill-activity")
@Validated
public class SeckillActivityController {

    @Resource
    private SeckillActivityService seckillActivityService;
    @Resource
    private ProductSpuApi productSpuApi;
    @Resource
    private ProductSubscriptionPlanApi subscriptionPlanApi;

    @PostMapping("/create")
    @Operation(summary = "创建秒杀活动")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:create')")
    public CommonResult<Long> createSeckillActivity(@Valid @RequestBody SeckillActivityCreateReqVO createReqVO) {
        validateEnterpriseProducts(List.of(createReqVO.getSpuId()));
        return success(seckillActivityService.createSeckillActivity(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新秒杀活动")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:update')")
    public CommonResult<Boolean> updateSeckillActivity(@Valid @RequestBody SeckillActivityUpdateReqVO updateReqVO) {
        validateEnterpriseProducts(List.of(updateReqVO.getSpuId()));
        seckillActivityService.updateSeckillActivity(updateReqVO);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭秒杀活动")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:close')")
    public CommonResult<Boolean> closeSeckillActivity(@RequestParam("id") Long id) {
        seckillActivityService.closeSeckillActivity(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除秒杀活动")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:delete')")
    public CommonResult<Boolean> deleteSeckillActivity(@RequestParam("id") Long id) {
        seckillActivityService.deleteSeckillActivity(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得秒杀活动")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query')")
    public CommonResult<SeckillActivityDetailRespVO> getSeckillActivity(@RequestParam("id") Long id) {
        SeckillActivityDO activity = seckillActivityService.getSeckillActivity(id);
        List<SeckillProductDO> products = seckillActivityService.getSeckillProductListByActivityId(id);
        return success(SeckillActivityConvert.INSTANCE.convert(activity, products));
    }

    @GetMapping("/page")
    @Operation(summary = "获得秒杀活动分页")
    @PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query')")
    public CommonResult<PageResult<SeckillActivityRespVO>> getSeckillActivityPage(@Valid SeckillActivityPageReqVO pageVO) {
        // 查询活动列表
        PageResult<SeckillActivityDO> pageResult = seckillActivityService.getSeckillActivityPage(pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty(pageResult.getTotal()));
        }

        // 拼接数据
        List<SeckillProductDO> products = seckillActivityService.getSeckillProductListByActivityIds(
                convertSet(pageResult.getList(), SeckillActivityDO::getId));
        List<ProductSpuRespDTO> spuList = productSpuApi.getSpuList(
                convertSet(pageResult.getList(), SeckillActivityDO::getSpuId)).getCheckedData();
        return success(SeckillActivityConvert.INSTANCE.convertPage(pageResult, products, spuList));
    }

    @GetMapping("/list-by-ids")
    @Operation(summary = "获得秒杀活动列表，基于活动编号数组")
    @Parameter(name = "ids", description = "活动编号数组", required = true, example = "[1024, 1025]")
    public CommonResult<List<SeckillActivityRespVO>> getSeckillActivityListByIds(@RequestParam("ids") List<Long> ids) {
        // 1. 获得开启的活动列表
        List<SeckillActivityDO> activityList = seckillActivityService.getSeckillActivityListByIds(ids);
        activityList.removeIf(activity -> CommonStatusEnum.isDisable(activity.getStatus()));
        if (CollUtil.isEmpty(activityList)) {
            return success(Collections.emptyList());
        }
        // 2. 拼接返回
        List<SeckillProductDO> productList = seckillActivityService.getSeckillProductListByActivityIds(
                convertList(activityList, SeckillActivityDO::getId));
        List<ProductSpuRespDTO> spuList = productSpuApi.getSpuList(convertList(activityList, SeckillActivityDO::getSpuId)).getCheckedData();
        return success(SeckillActivityConvert.INSTANCE.convertList(activityList, productList, spuList));
    }

    /**
     * 不在公开商城出售的商品不得进入公开促销活动（012 / R66 / FR-328）：
     * ① 企业专享商品；② 被会员授权计划引用的商品（个人授权商品 enterprise_only=0，同样不在公开商城）。
     */
    private void validateEnterpriseProducts(Collection<Long> spuIds) {
        if (CollUtil.isEmpty(spuIds)) {
            return;
        }
        boolean enterprise = productSpuApi.getSpuList(spuIds).getCheckedData().stream()
                .anyMatch(spu -> Boolean.TRUE.equals(spu.getEnterpriseOnly()));
        if (enterprise) {
            throw exception(SPU_ENTERPRISE_ONLY);
        }
        if (CollUtil.isNotEmpty(subscriptionPlanApi.getPlanSpuIds(spuIds).getCheckedData())) {
            throw exception(SPU_SUBSCRIPTION_ONLY);
        }
    }

}
