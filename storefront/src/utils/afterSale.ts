import { AfterSaleItemStatus, AfterSaleStatus, AfterSaleWay } from '@/types'
import { OrderStatus } from '@/types'

/**
 * 「能否申请退款」的判定与文案 —— 抽成纯函数是为了能**穷举**（照 `utils/orderStatus.ts`
 * 的 `canPay` / `canCancel` 先例）。
 *
 * 口径**全部来自后端**（`AfterSaleServiceImpl.validateOrderItemApplicable`），
 * 前端只做「明知会被拒就别给入口/别发请求」的减少，**不自己裁决业务规则**：
 *
 *   · 订单必须已支付且未取消 —— 待支付、已取消都会被后端拒；
 *   · 该订单项必须未被申请过 —— `afterSaleStatus !== 0` 会被拒；
 *   · 「退货退款」要求已发货；
 *   · 退款金额不得超过该项实付 —— 所以实付为 0 的项没有可退金额。
 *
 * ⚠️ **不做**的两件事：① 不自己加「收货超过 N 天不能退」这类时限（后端还是 TODO，
 * 要加也得后端先加）；② 不因为某一项在售后中而禁用整单（后端查重只看单项）。
 */

/** 判定用的订单项视图 —— 只取用得上的几个字段 */
export interface RefundableItem {
  payPrice?: number
  afterSaleStatus?: number
  /** 售后单编号 —— 撤销申请时要把它传给后端 */
  afterSaleId?: number
}

/** 后端允许申请售后的订单状态：已支付且未取消 */
const REFUNDABLE_ORDER_STATUS: number[] = [
  OrderStatus.UNDELIVERED,
  OrderStatus.DELIVERED,
  OrderStatus.COMPLETED,
]

/**
 * 该订单项现在能不能申请退款。
 *
 * 未知的 `afterSaleStatus`（后端将来新增状态）按**已申请过**保守处理 ——
 * 宁可少给一个入口，也不要发一枪注定被拒的请求。
 */
export function canApplyRefund(item: RefundableItem, orderStatus: number): boolean {
  if (!REFUNDABLE_ORDER_STATUS.includes(orderStatus)) return false
  if ((item.afterSaleStatus ?? AfterSaleItemStatus.NONE) !== AfterSaleItemStatus.NONE) return false
  // 实付为 0 或字段缺失 → 没有可退金额（后端 refundPrice 要求 > 0）
  return (item.payPrice ?? 0) > 0
}

/**
 * 该订单项能不能**撤销退款申请**（**订单详情页**用）。
 *
 * ⚠️ 后端允许撤销的**售后单**状态是「申请中 / 卖家同意 / 待卖家收货」三种，
 * 而订单项只暴露 `afterSaleStatus`（0/10/20），**看不出**售后单具体走到哪一步 ——
 * 所以这里只能在「售后中」时一律给入口，真到不可撤销时（例如商家已收货待退款）
 * 由后端拒，前端把它的文案透出来。**不猜**。
 *
 * ⚠️ 别和 {@link canCancelAfterSaleByStatus} 搞混：那个是**列表页**用的，
 * 输入是售后单的**精确状态**，判得比这里准。名字相近是刻意的，
 * 因为两者的输入不同 —— 拿错不会报错，只会静默判错。
 *
 * 没有 `afterSaleId` 就发不出撤销请求（那是接口的唯一参数），故不给入口。
 */
export function canCancelAfterSale(item: RefundableItem): boolean {
  return (
    (item.afterSaleStatus ?? AfterSaleItemStatus.NONE) === AfterSaleItemStatus.APPLY &&
    (item.afterSaleId ?? 0) > 0
  )
}

/** 后端只在售后单处于这三个状态时允许买家撤销（`AfterSaleServiceImpl.cancelAfterSale`） */
const CANCELABLE_AFTER_SALE_STATUS: number[] = [
  AfterSaleStatus.APPLY, // 10 申请中
  AfterSaleStatus.SELLER_AGREE, // 20 卖家同意
  AfterSaleStatus.BUYER_DELIVERY, // 30 待卖家收货
]

/**
 * 该售后单能不能撤销（**「我的售后」列表页**用）—— 输入是售后单的**精确状态**。
 *
 * ⚠️ 与 {@link canCancelAfterSale} 的区别就在输入：列表接口返回的是售后单的真实
 * `status`（10/20/30/40/50/61/62/63），所以这里能**准确**判断；而订单详情只有订单项的
 * 粗粒度状态，只能"售后中就给入口、由后端兜底"。**两个函数别互相替换。**
 */
export function canCancelAfterSaleByStatus(status?: number): boolean {
  return status !== undefined && CANCELABLE_AFTER_SALE_STATUS.includes(status)
}

/**
 * 「退货退款」现在能不能选 —— 只有**已发货/已完成**可以。
 * 未发货时后端会拒，所以界面上置灰并说明原因。
 */
export function allowsReturnRefund(orderStatus: number): boolean {
  return orderStatus === OrderStatus.DELIVERED || orderStatus === OrderStatus.COMPLETED
}

/**
 * 订单项的售后状态文案。**未售后返回空串**（不渲染标签）；
 * 未知状态同样回落空串，不抛错、不白屏。
 */
export function afterSaleItemStatusText(status?: number): string {
  switch (status) {
    case AfterSaleItemStatus.APPLY:
      return '退款处理中'
    case AfterSaleItemStatus.SUCCESS:
      return '已退款'
    default:
      return ''
  }
}

/**
 * 售后单状态的文案 —— **取自后端 `AfterSaleStatusEnum` 的 name 原文，不自己编**。
 * 未知状态回落为空串（后端将来新增状态时不至于白屏或显示成 `99`）。
 */
const STATUS_TEXTS: Record<number, string> = {
  [AfterSaleStatus.APPLY]: '申请中',
  [AfterSaleStatus.SELLER_AGREE]: '卖家已同意',
  [AfterSaleStatus.BUYER_DELIVERY]: '待卖家收货',
  [AfterSaleStatus.WAIT_REFUND]: '等待平台退款',
  [AfterSaleStatus.COMPLETE]: '退款完成',
  [AfterSaleStatus.BUYER_CANCEL]: '已取消申请',
  [AfterSaleStatus.SELLER_DISAGREE]: '卖家已拒绝',
  [AfterSaleStatus.SELLER_REFUSE]: '卖家拒绝收货',
}

export function afterSaleStatusText(status?: number): string {
  return status === undefined ? '' : (STATUS_TEXTS[status] ?? '')
}

const WAY_LABELS: Record<number, string> = {
  [AfterSaleWay.REFUND_ONLY]: '仅退款',
  [AfterSaleWay.RETURN_AND_REFUND]: '退货退款',
}

/** 售后方式的中文名 —— 与后端 `AfterSaleWayEnum` 的 name 一致 */
export function afterSaleWayLabel(way: number): string {
  return WAY_LABELS[way] ?? ''
}
