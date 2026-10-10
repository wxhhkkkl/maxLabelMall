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
/**
 * **售后单**的状态（后端 `AfterSaleStatusEnum`）。
 *
 * ⚠️ 与上面的 `AfterSaleItemStatus` **不是一个东西**，别混：
 * 这个是**售后单**的状态（8 个值），只有列表/详情接口会返回；
 * 那个是**订单项**的售后状态（3 个值），订单详情页只有它。
 * 「我的售后」列表拿到的是这里这套，所以能**准确**判断能否撤销。
 */
export const AfterSaleStatus = {
  /** 申请中 */
  APPLY: 10,
  /** 卖家通过（同意退款） */
  SELLER_AGREE: 20,
  /** 待卖家收货（买家已退货） */
  BUYER_DELIVERY: 30,
  /** 等待平台退款（卖家已收货） */
  WAIT_REFUND: 40,
  /** 完成 */
  COMPLETE: 50,
  /** 买家取消售后 */
  BUYER_CANCEL: 61,
  /** 卖家拒绝 */
  SELLER_DISAGREE: 62,
  /** 卖家拒绝收货 */
  SELLER_REFUSE: 63,
} as const

/** 「我的售后」列表项（后端 `AppAfterSaleRespVO`，字段很多，只列用到的） */
export interface AfterSaleListItem {
  id: number
  /** 售后单号 */
  no: string
  /** 售后单的**精确状态**，见 {@link AfterSaleStatus} */
  status: number
  /** 售后方式：10 仅退款 / 20 退货退款 */
  way: number
  applyReason: string
  applyDescription?: string
  /** 退款金额，单位：分 */
  refundPrice: number
  /** 申请时间（后端 epoch 毫秒） */
  createTime: number
  /** 卖家拒绝时给出的理由 */
  auditReason?: string
  orderId: number
  orderNo: string
  orderItemId: number
  spuName: string
  picUrl?: string
  /** 规格快照 */
  properties?: Array<{ propertyName: string; valueName: string }>
  count: number
}

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
