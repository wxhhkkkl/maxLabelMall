package cn.iocoder.yudao.module.product.controller.app.comment;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.app.comment.vo.AppCommentPageReqVO;
import cn.iocoder.yudao.module.product.controller.app.comment.vo.AppProductCommentRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.comment.ProductCommentDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.comment.ProductCommentService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 APP - 商品评价")
@RestController
@RequestMapping("/product/comment")
@Validated
public class AppProductCommentController {

    @Resource
    private ProductCommentService productCommentService;
    @Resource
    private ProductSpuService productSpuService;
    @Resource
    private MaxLabelSubscriptionPlanService subscriptionPlanService;

    @GetMapping("/page")
    @Operation(summary = "获得商品评价分页")
    @PermitAll
    public CommonResult<PageResult<AppProductCommentRespVO>> getCommentPage(@Valid AppCommentPageReqVO pageVO) {
        // 不在公开商城出售的商品，评价页同样不返回 —— 否则 spuName 会从这儿漏出去（FR-294）
        if (!isPublicSpu(pageVO.getSpuId())) {
            return success(PageResult.empty());
        }
        // 查询评论分页
        PageResult<ProductCommentDO> pageResult = productCommentService.getCommentPage(pageVO, Boolean.TRUE);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty(pageResult.getTotal()));
        }

        // 拼接返回
        pageResult.getList().forEach(item -> {
            if (Boolean.TRUE.equals(item.getAnonymous())) {
                item.setUserNickname(ProductCommentDO.NICKNAME_ANONYMOUS);
            }
        });
        return success(BeanUtils.toBean(pageResult, AppProductCommentRespVO.class));
    }

    /**
     * 该 SPU 是否在公开商城出售：企业专享商品与被授权计划引用的会员商品都不算。
     */
    private boolean isPublicSpu(Long spuId) {
        if (spuId == null) {
            return true;
        }
        ProductSpuDO spu = productSpuService.getSpu(spuId);
        if (spu == null) {
            return true; // 商品不存在就交给评论服务按原逻辑返回空
        }
        if (Boolean.TRUE.equals(spu.getEnterpriseOnly())) {
            return false;
        }
        return CollUtil.isEmpty(subscriptionPlanService.getPlanSpuIds(List.of(spuId)));
    }

}
