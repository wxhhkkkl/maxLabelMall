package cn.iocoder.yudao.module.trade.controller.app.invoice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppTradeOrderInvoiceCreateReqVO;
import cn.iocoder.yudao.module.trade.service.invoice.TradeOrderInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 订单开票（C 端）
 *
 * ⚠️ **下单时申请开票不在这里** —— 那是 `AppTradeOrderCreateReqVO#invoice`，与订单**同一事务**落库（Q32）。
 * 本控制器只管**订单已存在之后的自助补开**（US20），所以必然带上 `orderId`。
 *
 * @author 芋道源码
 */
@Tag(name = "用户 App - 订单开票")
@RestController
@RequestMapping("/trade/order-invoice")
@Validated
public class AppTradeOrderInvoiceController {

    @Resource
    private TradeOrderInvoiceService tradeOrderInvoiceService;

    @PostMapping("/create")
    @Operation(summary = "订单详情补开票", description = "限本人、已支付、未取消、且尚无申请的订单")
    public CommonResult<Long> createInvoice(@Valid @RequestBody AppTradeOrderInvoiceCreateReqVO reqVO) {
        // 会员身份一律从登录态取，**不接受请求传入** —— 否则谁都能给别人的订单挂开票信息
        return success(tradeOrderInvoiceService.createInvoiceByMember(getLoginUserId(), reqVO));
    }

}
