package cn.iocoder.yudao.module.trade.enums.invoice;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * 开票申请状态的枚举
 *
 * 状态流转（specs/006-order-invoice 的 data-model §1.1）：
 * <pre>
 *   待开票(0) --后台开具并上传文件--> 已开票(10)
 *   待开票(0) --订单取消 / 全额退款取消--> 已作废(20)   [终态]
 *   已开票(10) --重新上传--> 已开票(10)（覆盖文件）
 * </pre>
 *
 * ⚠️ **没有"待支付"这一态**：申请在**下单时**就存在（客户可能还没付款），
 * "订单是否已支付"由**订单**裁决 —— 后台「待开票」列表的分页 join `trade_order`
 * 并带 `pay_status = 1` 条件。把支付状态在开票表里存第二份，会在支付路径绕过埋点时
 * **静默停在错误状态**（不报错的丢单），所以刻意不存。
 *
 * ⚠️ **已开票的不因退款 / 取消而回退**（Q30）：票已在客户手里，系统不替财务撤回。
 *
 * @author 芋道源码
 */
@AllArgsConstructor
@Getter
public enum TradeOrderInvoiceStatusEnum implements ArrayValuable<Integer> {

    /**
     * 待开票：申请已提交，等后台开具
     */
    WAITING(0, "待开票"),
    /**
     * 已开票：后台已上传发票文件
     */
    ISSUED(10, "已开票"),
    /**
     * 已作废：订单取消 / 全额退款导致，终态
     */
    CANCELED(20, "已作废"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(TradeOrderInvoiceStatusEnum::getStatus).toArray(Integer[]::new);

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 状态名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static TradeOrderInvoiceStatusEnum valueOf(Integer status) {
        return firstMatch(value -> value.getStatus().equals(status), values());
    }

}
