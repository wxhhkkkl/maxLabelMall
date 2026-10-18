package cn.iocoder.yudao.module.trade.service.offline;

import cn.iocoder.yudao.module.trade.dal.dataobject.offline.TradeOrderOfflinePaymentDO;
import cn.iocoder.yudao.module.trade.service.offline.bo.TradeOrderOfflineConfirmReqBO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 对公转账信息的 Service 接口（008）
 *
 * ⚠️ **为什么是一个独立服务，而不是塞进 `TradeOrderUpdateServiceImpl`**：
 * 那个类要 mock 商品 / 营销 / 会员 / 地址一整套 API 才能测（本仓库至今没有它的测试类）；
 * 而本服务只依赖 mapper，可以用 `BaseDbUnitTest` **轻量地测**。
 * 与 006 把发票单独拆成 `TradeOrderInvoiceService` 是同一个做法（原则 I：测试先行要有地方落）。
 *
 * @author 芋道源码
 */
public interface TradeOrderOfflinePaymentService {

    /**
     * 为一笔订单记下"它走对公转账"—— **下单时调用**
     *
     * ⚠️ 只插一行、**不带任何确认信息**（那要等财务确认收款时才填）。
     *
     * @param orderId 订单编号
     */
    void createOfflinePayment(Long orderId);

    /**
     * 查询订单的对公转账信息
     *
     * @param orderId 订单编号
     * @return 对公转账信息；**不是对公单时返回 null**
     */
    TradeOrderOfflinePaymentDO getOfflinePaymentByOrderId(Long orderId);

    /**
     * 批量查询多笔订单的对公转账信息 —— 订单列表要按它判断"是不是对公单"
     *
     * @param orderIds 订单编号集合
     * @return 对公转账信息列表
     */
    List<TradeOrderOfflinePaymentDO> getOfflinePaymentListByOrderIds(Collection<Long> orderIds);

    /**
     * 该订单是不是**还没确认收款**的对公单 —— 即「待收款」（FR-200）
     *
     * @param orderId 订单编号
     * @return 是则返回 true
     */
    boolean isWaitingReceipt(Long orderId);

    /**
     * 批量判断哪些订单是**还没确认收款**的对公单 —— 订单列表要按它决定
     * 显示「待收款」还是「待支付」、要不要给「立即支付」入口（FR-200）
     *
     * ⚠️ 与 {@link #isWaitingReceipt(Long)} **同一口径**：只判自己这张表，
     * "订单是否还是未支付"由调用方结合订单一起判。
     *
     * @param orderIds 订单编号集合
     * @return 还没确认收款的对公单编号集合
     */
    Set<Long> getWaitingReceiptOrderIds(Collection<Long> orderIds);

    /**
     * 回填"已收款"的痕迹 —— **确认收款时调用**（FR-204 / FR-205 / FR-206）
     *
     * ⚠️ **这是"确认收款"的幂等闸门**：内部是**条件更新**
     * （`confirm_time IS NULL` 才会被改到），所以重复 / 并发提交**最多生效一次**（R44）。
     * 调用方 MUST 先看返回值：`false` = 这单已经被确认过了（或根本不是对公单），
     * **不许**再往下造支付单。
     *
     * ⚠️ 流水号的非空校验**不在这里** —— 那是"确认收款这个动作"的约束，
     * 由调用方（订单域）在更早的地方拦（FR-204）。
     *
     * @param reqBO 确认信息（见 {@link TradeOrderOfflineConfirmReqBO}）
     * @return 是否真的写成功；`false` 表示没改到任何一行
     */
    boolean confirmReceipt(TradeOrderOfflineConfirmReqBO reqBO);

}
