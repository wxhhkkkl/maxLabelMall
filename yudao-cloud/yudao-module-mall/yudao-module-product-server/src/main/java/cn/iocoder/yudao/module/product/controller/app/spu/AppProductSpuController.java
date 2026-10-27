package cn.iocoder.yudao.module.product.controller.app.spu;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuDetailRespVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.service.history.ProductBrowseHistoryService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_ENTERPRISE_ONLY;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_NOT_ENABLE;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_NOT_EXISTS;

@Tag(name = "用户 APP - 商品 SPU")
@RestController
@RequestMapping("/product/spu")
@Validated
public class AppProductSpuController {

    @Resource
    private ProductSpuService productSpuService;
    @Resource
    private ProductSkuService productSkuService;
    @Resource
    private ProductBrowseHistoryService productBrowseHistoryService;
    @Resource
    private MemberOrgApi memberOrgApi;
    @Resource
    private MaxLabelSubscriptionPlanService subscriptionPlanService;

    @GetMapping("/list-by-ids")
    @Operation(summary = "获得商品 SPU 列表")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PermitAll
    public CommonResult<List<AppProductSpuRespVO>> getSpuList(@RequestParam("ids") Set<Long> ids) {
        List<ProductSpuDO> list = productSpuService.getSpuList(ids);
        if (CollUtil.isEmpty(list)) {
            return success(Collections.emptyList());
        }
        // 企业专享商品：只对企业 OWNER/ADMIN 返回（购物车/订单渲染需要；012 / Q72）
        boolean enterpriseAdmin = isEnterpriseAdmin();
        List<ProductSpuDO> visibleList = enterpriseAdmin ? list
                : list.stream().filter(spu -> !Boolean.TRUE.equals(spu.getEnterpriseOnly())).toList();
        // 会员授权商品（被订阅计划引用）不进入公开商城，首页货架走的就是这个接口（FR-328 / Q81）
        if (!enterpriseAdmin) {
            List<Long> planSpuIds = subscriptionPlanService.getPlanSpuIds(ids);
            if (CollUtil.isNotEmpty(planSpuIds)) {
                visibleList = visibleList.stream().filter(spu -> !planSpuIds.contains(spu.getId())).toList();
            }
        }

        // 拼接返回
        visibleList.forEach(spu -> spu.setSalesCount(spu.getSalesCount() + spu.getVirtualSalesCount()));
        List<AppProductSpuRespVO> voList = BeanUtils.toBean(visibleList, AppProductSpuRespVO.class);
        return success(voList);
    }

    @GetMapping("/page")
    @Operation(summary = "获得商品 SPU 分页")
    @PermitAll
    public CommonResult<PageResult<AppProductSpuRespVO>> getSpuPage(@Valid AppProductSpuPageReqVO pageVO) {
        PageResult<ProductSpuDO> pageResult = productSpuService.getSpuPage(pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty(pageResult.getTotal()));
        }

        // 拼接返回
        pageResult.getList().forEach(spu -> spu.setSalesCount(spu.getSalesCount() + spu.getVirtualSalesCount()));
        PageResult<AppProductSpuRespVO> voPageResult = BeanUtils.toBean(pageResult, AppProductSpuRespVO.class);
        return success(voPageResult);
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得商品 SPU 明细")
    @Parameter(name = "id", description = "编号", required = true)
    @PermitAll
    public CommonResult<AppProductSpuDetailRespVO> getSpuDetail(@RequestParam("id") Long id) {
        // 获得商品 SPU
        ProductSpuDO spu = productSpuService.getSpu(id);
        if (spu == null) {
            throw exception(SPU_NOT_EXISTS);
        }
        // 企业专享商品不通过公开详情暴露（企业管理员走企业计划专用接口）
        if (Boolean.TRUE.equals(spu.getEnterpriseOnly())) {
            throw exception(SPU_ENTERPRISE_ONLY);
        }
        if (!ProductSpuStatusEnum.isEnable(spu.getStatus())) {
            throw exception(SPU_NOT_ENABLE, spu.getName());
        }
        // 获得商品 SKU
        List<ProductSkuDO> skus = productSkuService.getSkuListBySpuId(spu.getId());

        // 增加浏览量
        productSpuService.updateBrowseCount(id, 1);
        // 保存浏览记录
        productBrowseHistoryService.createBrowseHistory(getLoginUserId(), id);

        // 拼接返回
        spu.setSalesCount(spu.getSalesCount() + spu.getVirtualSalesCount());
        AppProductSpuDetailRespVO spuVO = BeanUtils.toBean(spu, AppProductSpuDetailRespVO.class)
                .setSkus(BeanUtils.toBean(skus, AppProductSpuDetailRespVO.Sku.class));
        return success(spuVO);
    }

    /** 当前登录会员是否是启用企业的 OWNER/ADMIN */
    private boolean isEnterpriseAdmin() {
        Long userId = getLoginUserId();
        if (userId == null) {
            return false;
        }
        MemberOrgRespDTO org = memberOrgApi.getByMemberId(userId).getCheckedData();
        return org != null && Integer.valueOf(0).equals(org.getStatus())
                && (Integer.valueOf(1).equals(org.getRole()) || Integer.valueOf(2).equals(org.getRole()));
    }

}
