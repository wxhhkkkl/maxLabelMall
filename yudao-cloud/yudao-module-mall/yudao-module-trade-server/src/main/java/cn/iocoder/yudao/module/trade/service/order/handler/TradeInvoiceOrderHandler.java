package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.invoice.TradeOrderInvoiceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 开票申请的 {@link TradeOrderHandler} 实现类（006 / Q30 / FR-184）
 *
 * **订单取消 / 退款时撤回开票申请**：交易既然已经退回，票就不该还在 ——
 * 留在待开票列表里等于给运营埋一个"照单开票"的坑，客户侧也会停在一个永远等不到结果的「待开票」上。
 *
 * ⚠️ **已开票的也一并撤回**（2026-10-15 所有者口径）：前台不再显示、后台也不能再更新。
 * 红冲本身仍由财务线下处理，但系统里这张票的**可用性**必须立刻消失。
 *
 * ⚠️ **两个钩子都要实现**，因为它们是两条不同的路：
 *   · `afterCancelOrder`     —— 订单**取消**（C 端取消 / 系统超时 / 全额退款导致的取消）
 *   · `afterCancelOrderItem` —— **部分退款**（订单不取消，只退其中一项）
 * 少了后者就会出现"退了一件商品、票还挂在前台"。两者可能都触发，服务层作废是幂等的。
 *
 * ⚠️ **为什么走扩展点，而不是在 `cancelOrderByMember` / `cancelOrderBySystem` /
 * `cancelOrderByAfterSale` 三处各插一行**：
 * 撞的是同一件事的三个入口，将来上游再加第四个取消入口时**必然漏掉**，而漏掉的后果是
 * "票留在了一笔已退款的交易上" —— **不报错**。用扩展点则新增入口自动覆盖。
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class TradeInvoiceOrderHandler implements TradeOrderHandler {

    @Resource
    private TradeOrderInvoiceService tradeOrderInvoiceService;

    @Override
    public void afterCancelOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        // 传的是**订单编号**：申请是按订单找的（一个订单至多一条）
        tradeOrderInvoiceService.cancelInvoiceByOrderId(order.getId());
    }

    /**
     * **部分退款**也走这里 —— 它**不取消订单**，所以 `afterCancelOrder` 不会触发。
     *
     * 2026-10-15 所有者的口径是"退款了票就该撤回"，没有区分全退还是部分退；
     * 少了这个钩子，就会出现"退了一件商品、票还挂在前台"。
     *
     * ⚠️ 与 `afterCancelOrder` 可能先后都触发（全额退款取消订单时两边都会走）——
     * 服务层的作废是**幂等**的（已经作废的再调一次影响 0 行），所以重复调用是安全的。
     */
    @Override
    public void afterCancelOrderItem(TradeOrderDO order, TradeOrderItemDO orderItem) {
        tradeOrderInvoiceService.cancelInvoiceByOrderId(order.getId());
    }

}
