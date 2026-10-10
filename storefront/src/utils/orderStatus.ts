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

/**
 * 能否评价**这个订单项**（FR-073）。
 *
 * ⚠️ 三个条件缺一不可，而且它们**层级不同** —— 这是最容易写错的地方：
 *
 * 1. **订单状态必须是「已完成」**（后端拦这个）；
 * 2. **订单级** `commentStatus` 必须为 `false` —— 后端也拦这个：订单整体评过就拒绝，
 *    报「创建交易订单项的评价失败，订单已评价」；
 * 3. **订单项级** `commentStatus` 必须为 `false` —— 后端**不拦**这个（它只看订单级的），
 *    重复提交是 product 层按 `(userId, orderItemId)` 挡的，报「订单的商品评价已存在」。
 *
 * 所以只判其中一个就会给出"点了必然被拒"的按钮：只判订单级 → 已评过的项还显示入口；
 * 只判订单项级 → 订单没完成时也显示入口。
 */
export function canCommentItem(
  order: { status: number; commentStatus?: boolean },
  item: { commentStatus?: boolean },
): boolean {
  return order.status === OrderStatus.COMPLETED && !order.commentStatus && !item.commentStatus
}

/** 订单列表页：整单是否**还有项可评价**（有则给「去评价」入口）。粒度是订单，不是订单项 */
export function canCommentOrder(order: {
  status: number
  commentStatus?: boolean
  items?: Array<{ commentStatus?: boolean }>
}): boolean {
  if (order.status !== OrderStatus.COMPLETED || order.commentStatus) return false
  return (order.items ?? []).some((it) => !it.commentStatus)
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
