import { OrderStatus, type OrderStatusValue } from '@/types'

/**
 * 订单状态映射 —— **与后端 `TradeOrderStatusEnum` 一一对应**（FR-041a）。
 *
 * ⚠️ 关键语义：**支付成功后订单进入 `10 待发货`，不是「已支付」**（FR-039）。
 * 这是本项目最容易实现错的一处映射，`orderStatus.spec.ts` 为此专门设了断言。
 *
 * 前端 **MUST NOT** 归并状态或自定义命名；状态权威在后端。
 */

export const ORDER_STATUS_TEXT: Record<OrderStatusValue, string> = {
  [OrderStatus.UNPAID]: '待支付',
  [OrderStatus.UNDELIVERED]: '待发货',
  [OrderStatus.DELIVERED]: '已发货',
  [OrderStatus.COMPLETED]: '已完成',
  [OrderStatus.CANCELED]: '已取消',
}

/**
 * C6 状态标签的变体名。与 `store.css` 中的 `.ml-pill.is-*` 一一对应。
 *
 * 早期版本用过 `.is-paid` 表示「已支付」—— 那是**不可实现的**：后端五个状态里
 * 没有「已支付」，支付成功后是「待发货」。现改为 `.is-awaiting-shipment`。
 */
export type PillVariant =
  | 'is-pending'
  | 'is-awaiting-shipment'
  | 'is-shipped'
  | 'is-done'
  | 'is-cancelled'

export const ORDER_STATUS_VARIANT: Record<OrderStatusValue, PillVariant> = {
  [OrderStatus.UNPAID]: 'is-pending',
  [OrderStatus.UNDELIVERED]: 'is-awaiting-shipment',
  [OrderStatus.DELIVERED]: 'is-shipped',
  [OrderStatus.COMPLETED]: 'is-done',
  [OrderStatus.CANCELED]: 'is-cancelled',
}

/** 状态文案。未知状态回落为「未知」，不抛错（后端新增状态时不至于白屏） */
export function orderStatusText(status: number): string {
  return ORDER_STATUS_TEXT[status as OrderStatusValue] ?? '未知'
}

export function orderStatusVariant(status: number): PillVariant {
  return ORDER_STATUS_VARIANT[status as OrderStatusValue] ?? 'is-done'
}

/** 仅「待支付」提供用户侧动作（支付、取消） */
export function canPay(status: number): boolean {
  return status === OrderStatus.UNPAID
}

export function canCancel(status: number): boolean {
  return status === OrderStatus.UNPAID
}

/** 终态：已完成 / 已取消 */
export function isTerminal(status: number): boolean {
  return status === OrderStatus.COMPLETED || status === OrderStatus.CANCELED
}

/** 订单列表的筛选胶囊。顺序与设计一致：全部在最前 */
export function orderStatusFilters(): Array<{ value: OrderStatusValue | ''; label: string }> {
  return [
    { value: '', label: '全部' },
    { value: OrderStatus.UNPAID, label: ORDER_STATUS_TEXT[OrderStatus.UNPAID] },
    { value: OrderStatus.UNDELIVERED, label: ORDER_STATUS_TEXT[OrderStatus.UNDELIVERED] },
    { value: OrderStatus.DELIVERED, label: ORDER_STATUS_TEXT[OrderStatus.DELIVERED] },
    { value: OrderStatus.COMPLETED, label: ORDER_STATUS_TEXT[OrderStatus.COMPLETED] },
    { value: OrderStatus.CANCELED, label: ORDER_STATUS_TEXT[OrderStatus.CANCELED] },
  ]
}
