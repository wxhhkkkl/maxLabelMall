import type { Address } from './address'
import type { SettlementCoupon } from './coupon'

/**
 * 订单状态 —— 与后端 TradeOrderStatusEnum **一一对应，不得归并或自定义命名**（FR-041a）。
 *
 * 关键语义：**支付成功后进入 10 待发货，不是「已支付」**（FR-039）。
 */
export const OrderStatus = {
  UNPAID: 0,
  UNDELIVERED: 10,
  DELIVERED: 20,
  COMPLETED: 30,
  CANCELED: 40,
} as const

export type OrderStatusValue = (typeof OrderStatus)[keyof typeof OrderStatus]

/** 金额构成。**全部单位：分。** */
export interface OrderPrice {
  totalPrice: number
  discountPrice: number
  deliveryPrice: number
  couponPrice: number
  pointPrice: number
  vipPrice: number
  payPrice: number
}

/** 结算响应（AppTradeOrderSettlementRespVO） */
export interface SettlementResp {
  type: number
  items: SettlementItem[]
  /** 可用 + 不可用，含 match / mismatchReason */
  coupons: SettlementCoupon[]
  price: OrderPrice
  address?: Address | null
  usePoint: number
  totalPoint: number
  promotions: unknown[]
}

export interface SettlementItem {
  categoryId: number
  spuId: number
  spuName: string
  skuId: number
  price: number
  picUrl: string
  properties: Array<{
    propertyId: number
    propertyName: string
    valueId: number
    valueName: string
  }>
  cartId?: number
  count: number
}

/** 订单列表项 */
export interface OrderPageItem {
  id: number
  no: string
  status: OrderStatusValue
  /**
   * ⚠️ 时间字段是 **epoch 毫秒数**，不是字符串 —— yudao 全局给 `LocalDateTime`
   * 注册了 `TimestampLocalDateTimeSerializer`。渲染 MUST 过 `utils/time.ts`。
   */
  createTime: number | string
  payPrice: number
  /**
   * **订单整体是否已评价**（后端 `AppTradeOrderPageItemRespVO.commentStatus`）。
   *
   * ⚠️ 这是**订单级**的：只有当订单里**所有**订单项都评过，后端才会把它置为 `true`。
   * 它与下面订单项级的 `commentStatus` **是两个不同的判定**，评价入口要**同时**看两个
   * （后端拦的是订单级，重复提交是 product 层按订单项拦的）。
   */
  commentStatus?: boolean
  /**
   * 订单项。**名称 / 规格 / 单价 / 数量都是下单时的快照** —— 商品后续改名、
   * 换规格、调价都不应让历史订单跟着变。
   */
  items: Array<{
    id: number
    spuName: string
    picUrl: string
    /** 规格快照（后端 `properties`） */
    properties?: Array<{ propertyName: string; valueName: string }>
    count: number
    /** 商品原价（单价），单位：分 */
    price: number
    /** 该项应付金额（总），单位：分。**也是「申请退款」的金额上限**（后端按它校验） */
    payPrice?: number
    /** **该订单项是否已评价**。与订单级的 `commentStatus` 是两回事，见上 */
    commentStatus?: boolean
    /**
     * 该订单项的**售后**编号与状态（后端 `AppTradeOrderItemRespVO`）。
     * `afterSaleStatus`：0 未售后 / 10 售后中 / 20 售后成功 —— 见 `@/types/afterSale`。
     *
     * ⚠️ 这是**订单项级**的：一笔订单里不同商品各自独立，某项在售后中**不影响**其它项申请。
     * ⚠️ 卖家拒绝后后端会把它重置回 0，所以别在前端缓存"申请过"。
     */
    afterSaleId?: number
    afterSaleStatus?: number
  }>
}

/**
 * 订单详情。
 *
 * ⚠️ **金额字段是平铺的，没有嵌套的 `price` 对象** —— 后端 `AppTradeOrderDetailRespVO`
 * 把 totalPrice / discountPrice / deliveryPrice / payPrice / couponPrice / pointPrice /
 * vipPrice 直接挂在自己身上。早期这里按「嵌套 price」建模，视图读 `order.price.totalPrice`
 * 直接抛 TypeError（`Cannot read properties of undefined`）。
 *
 * 本接口**结构上满足 `OrderPrice`**，所以 `reconcile(order)` 可以直接用（SC-007）。
 */
export interface OrderDetail extends OrderPageItem, OrderPrice {
  /** epoch 毫秒数（见 `createTime` 的说明） */
  payExpireTime: number | string
  payTime?: number | string
  payChannelName?: string
  /**
   * 支付单号 —— 提交支付时要用的 id（是它，**不是交易订单号 `id`**）。
   *
   * ⚠️ **可能为 null**：`payPrice === 0` 的订单后端不创建支付单
   * （`TradeOrderUpdateServiceImpl` 里 `createPayOrder` 被
   * `if (order.getPayPrice() > 0)` 包着，而它是 `payOrderId` 的唯一写入点）。
   * 这种情况没有任何 id 可提交，前端 MUST 走「本单无需支付」那条路，
   * **不得把 null 提交给支付接口**（FR-037）。
   */
  payOrderId?: number | null
  receiverName: string
  receiverMobile: string
  receiverAreaName?: string
  receiverDetailAddress: string
  couponId?: number
  couponPrice: number
  pointPrice: number
  vipPrice: number
}

/**
 * 下单请求。**`pointStatus` 是必填**（后端标 @NotNull），本期固定传 false。
 */
export interface OrderCreateReq {
  items: Array<{ skuId: number; count: number; cartId?: number }>
  couponId?: number
  addressId?: number
  /** 必填，本期固定 false（不展示积分） */
  pointStatus: boolean
  /** 本期固定按快递（FR-027a） */
  deliveryType?: number
  remark?: string
}

/** 下单响应。**payPrice 为 0 的订单 `payOrderId` 是 null**，前端必须处理。 */
export interface OrderCreateResp {
  id: number
  payOrderId: number | null
}
