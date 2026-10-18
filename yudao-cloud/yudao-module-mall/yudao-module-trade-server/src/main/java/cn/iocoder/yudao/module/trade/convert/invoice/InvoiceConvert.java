package cn.iocoder.yudao.module.trade.convert.invoice;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.admin.invoice.vo.TradeOrderInvoiceRespVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppOrderInvoiceRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeOrderInvoiceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTitleKindEnum;
import cn.iocoder.yudao.module.trade.enums.invoice.InvoiceTypeEnum;
import cn.iocoder.yudao.module.trade.enums.invoice.TradeOrderInvoiceStatusEnum;

/**
 * 开票申请的转换
 *
 * ⚠️ **手写而不是 MapStruct**：`typeName` 是**由枚举算出来的**（不是 DO 上的字段），
 * MapStruct 要表达它得写 expression，反而不如两行代码清楚。
 * 字段名一一对应的部分仍走 {@link BeanUtils#toBean}（本仓库的惯用法）。
 *
 * @author 芋道源码
 */
public final class InvoiceConvert {

    private InvoiceConvert() {
    }

    /**
     * DO → C 端订单详情里的 `invoice` 对象
     *
     * @param invoice 申请；**为 null 时返回 null** —— 前端靠它区分"没申请"与"申请了"（FR-176）
     */
    public static AppOrderInvoiceRespVO convertToAppRespVO(TradeOrderInvoiceDO invoice) {
        if (invoice == null) {
            return null;
        }
        AppOrderInvoiceRespVO respVO = BeanUtils.toBean(invoice, AppOrderInvoiceRespVO.class);
        // DO 上的字段是 invoiceFileUrl，响应里叫 fileUrl（前端契约）
        respVO.setFileUrl(invoice.getInvoiceFileUrl());
        InvoiceTypeEnum type = InvoiceTypeEnum.valueOf(invoice.getType());
        respVO.setTypeName(type != null ? type.getName() : "");
        InvoiceTitleKindEnum titleKind = InvoiceTitleKindEnum.valueOf(invoice.getTitleKind());
        respVO.setTitleKindName(titleKind != null ? titleKind.getName() : "");
        return respVO;
    }

    /**
     * DO + 订单 → 后台列表的 VO
     *
     * ⚠️ `amount` 是**申请时**的实付快照，而 `orderPayPrice` / `orderRefundPrice` 是订单**当前**的数 ——
     * 两个都带上，运营开具前才看得出"这笔已经退过一部分"（Q33：部分退款不自动作废、也不改金额）。
     */
    public static TradeOrderInvoiceRespVO convertToAdminRespVO(TradeOrderInvoiceDO invoice, TradeOrderDO order) {
        TradeOrderInvoiceRespVO respVO = BeanUtils.toBean(invoice, TradeOrderInvoiceRespVO.class);
        InvoiceTypeEnum type = InvoiceTypeEnum.valueOf(invoice.getType());
        respVO.setTypeName(type != null ? type.getName() : "");
        InvoiceTitleKindEnum titleKind = InvoiceTitleKindEnum.valueOf(invoice.getTitleKind());
        respVO.setTitleKindName(titleKind != null ? titleKind.getName() : "");
        TradeOrderInvoiceStatusEnum status = TradeOrderInvoiceStatusEnum.valueOf(invoice.getStatus());
        respVO.setStatusName(status != null ? status.getName() : "");
        if (order != null) {
            respVO.setOrderPayPrice(order.getPayPrice());
            respVO.setOrderRefundPrice(order.getRefundPrice());
        }
        return respVO;
    }

}
