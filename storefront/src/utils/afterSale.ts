import { AfterSaleItemStatus, AfterSaleWay } from '@/types'
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

/** 判定用的订单项视图 —— 只取用得上的两个字段 */
export interface RefundableItem {
  payPrice?: number
  afterSaleStatus?: number
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

const WAY_LABELS: Record<number, string> = {
  [AfterSaleWay.REFUND_ONLY]: '仅退款',
  [AfterSaleWay.RETURN_AND_REFUND]: '退货退款',
}

/** 售后方式的中文名 —— 与后端 `AfterSaleWayEnum` 的 name 一致 */
export function afterSaleWayLabel(way: number): string {
  return WAY_LABELS[way] ?? ''
}
