package cn.iocoder.yudao.module.trade.controller.app.customization;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.app.customization.vo.*;
import cn.iocoder.yudao.module.trade.service.customization.CustomizationMaterialService;
import cn.iocoder.yudao.module.trade.service.customization.CustomizationOrderService;
import cn.iocoder.yudao.module.trade.service.customization.CustomizationPriceService;
import cn.iocoder.yudao.module.trade.service.customization.CustomizationSpecConvert;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationOrderCreateResultBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationQuoteBO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_PRICE_CHANGED;

/**
 * 用户 App - 印刷定制（014）
 */
@Tag(name = "用户 App - 印刷定制")
@RestController
@RequestMapping("/trade/customization")
@Validated
public class AppCustomizationController {

    @Resource
    private CustomizationMaterialService customizationMaterialService;
    @Resource
    private CustomizationPriceService customizationPriceService;
    @Resource
    private CustomizationOrderService customizationOrderService;

    @GetMapping("/material/list")
    @Operation(summary = "获得可选的定制材质")
    @PermitAll
    public CommonResult<List<AppCustomizationMaterialRespVO>> getMaterialList() {
        return success(BeanUtils.toBean(customizationMaterialService.getEnabledMaterialList(),
                AppCustomizationMaterialRespVO.class));
    }

    @PostMapping("/quote")
    @Operation(summary = "定制报价（不落库）")
    @PermitAll
    public CommonResult<AppCustomizationQuoteRespVO> quote(@Valid @RequestBody AppCustomizationQuoteReqVO reqVO) {
        CustomizationQuoteBO quote = customizationPriceService.quote(CustomizationSpecConvert.toSpec(reqVO),
                getLoginUserId(), reqVO.getAddressId(), reqVO.getDeliveryType());
        return success(buildQuoteResp(quote));
    }

    @PostMapping("/order/create")
    @Operation(summary = "创建定制订单")
    public CommonResult<AppCustomizationOrderCreateRespVO> createOrder(
            @Valid @RequestBody AppCustomizationOrderCreateReqVO reqVO) {
        CustomizationOrderCreateResultBO result = customizationOrderService.createOrder(getLoginUserId(), reqVO);
        AppCustomizationOrderCreateRespVO resp = new AppCustomizationOrderCreateRespVO();
        resp.setChanged(result.isChanged());
        if (result.isChanged()) {
            resp.setQuote(buildQuoteResp(result.getQuote()));
            // 价格已变（Q101）：不建单，返回新报价；用 `changed` 标记而不是错误码，
            // 因为前端请求层在非 0 code 时会丢掉 data（见 contracts §3）
            return success(resp);
        }
        resp.setId(result.getOrderId());
        resp.setPayOrderId(result.getPayOrderId());
        resp.setCustomizationOrderId(result.getCustomizationOrderId());
        return success(resp);
    }

    static AppCustomizationQuoteRespVO buildQuoteResp(CustomizationQuoteBO quote) {
        if (quote == null) {
            return null;
        }
        AppCustomizationQuoteRespVO resp = new AppCustomizationQuoteRespVO();
        resp.setMaterialId(quote.getMaterialId());
        resp.setMaterialName(quote.getMaterialName());
        resp.setUnitArea(quote.getUnitArea());
        resp.setTotalArea(quote.getTotalArea());
        resp.setFreight(quote.getFreight());
        resp.setPayable(quote.getPayable());
        resp.setQuoteTime(quote.getQuoteTime());
        if (quote.getBreakdown() != null) {
            AppCustomizationQuoteRespVO.Breakdown breakdown = new AppCustomizationQuoteRespVO.Breakdown();
            breakdown.setPrint(quote.getBreakdown().getPrint());
            breakdown.setPrinting(quote.getBreakdown().getPrinting());
            breakdown.setDieCut(quote.getBreakdown().getDieCut());
            breakdown.setUrgent(quote.getBreakdown().getUrgent());
            breakdown.setSubtotal(quote.getBreakdown().getSubtotal());
            breakdown.setTotal(quote.getBreakdown().getTotal());
            resp.setBreakdown(breakdown);
        }
        return resp;
    }

}
