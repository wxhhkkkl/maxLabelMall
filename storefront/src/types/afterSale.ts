/**
 * 售后（退款）相关的枚举与请求类型 —— **与后端一一对应**，不要自行归并或改名。
 *
 * 走后端既有的售后接口（`/app-api/trade/after-sale/*`），后端与管理端都是现成的。
 */

/**
 * 售后方式（后端 `AfterSaleWayEnum`）。
 *
 * ⚠️ 是 **10 / 20**，不是 1 / 2。
 * ⚠️ `RETURN_AND_REFUND` 要求订单**已发货** —— 未发货时后端会拒
 * （「订单未发货，无法申请【退货退款】售后」）。
 */
export const AfterSaleWay = {
  /** 仅退款 */
  REFUND_ONLY: 10,
  /** 退货退款 */
  RETURN_AND_REFUND: 20,
} as const

export type AfterSaleWayValue = (typeof AfterSaleWay)[keyof typeof AfterSaleWay]

/**
 * **订单项**的售后状态（后端 `TradeOrderItemAfterSaleStatusEnum`）。
 *
 * ⚠️ 注意这是**订单项级**的，不是订单级 —— 一笔订单里不同商品各自独立。
 * ⚠️ 卖家拒绝售后时，后端会把它**重置回 `NONE`**，所以"申请过"这件事
 * **不能在前端缓存**，每次都得看后端给的值。
 */
export const AfterSaleItemStatus = {
  NONE: 0,
  /** 售后中 */
  APPLY: 10,
  /** 售后成功 */
  SUCCESS: 20,
} as const

/** 申请售后（`POST /app-api/trade/after-sale/create`）的请求体 */
export interface AfterSaleCreateReq {
  /** **订单项**编号 —— 不是订单编号 */
  orderItemId: number
  way: number
  /** 退款金额，单位：分。**不得超过该订单项的实付金额**（后端会校验） */
  refundPrice: number
  /** 申请原因（必填） */
  applyReason: string
  /** 补充描述 */
  applyDescription?: string
  /** 补充凭证图片 */
  applyPicUrls?: string[]
}
