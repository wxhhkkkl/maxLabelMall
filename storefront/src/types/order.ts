import type { Address } from './address'
import type { SettlementCoupon } from './coupon'
import type { InvoiceApplyForm, OrderInvoice } from './invoice'

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
  /**
   * 本单**实际采用**的配送方式（1 快递 / 2 自提 / 3 虚拟商品）。
   *
   * ⚠️ 由**服务端**按商品判定（结算时不传 deliveryType）—— 前端只据此决定
   * "要不要收货地址"（3 = 虚拟商品，不要），并在**下单时原样回传**。
   * 前端 MUST NOT 自己判断"这单算不算虚拟"。
   */
  deliveryType: number
  items: SettlementItem[]
  /** 可用 + 不可用，含 match / mismatchReason */
  coupons: SettlementCoupon[]
  price: OrderPrice
  address?: Address | null
  /**
   * 是否对客户开放「对公转账」（008 / FR-199）。
   *
   * ⚠️ **结算页只靠这一个布尔决定"要不要显示对公转账"** —— 前端 MUST NOT 自己判断，
   * 也 MUST NOT 猜收款账户（账户明细在**订单详情**里给，结算响应里没有）。
   * 后台关掉（`trade_config.offline_pay_enabled = false`）时这里就是 `false`（FR-210）。
   */
  offlinePayEnabled: boolean
  /** 对公转账的提示语（如「请在转账附言里写订单号」）—— 后台可配，可为空 */
  offlinePayTip?: string
  usePoint: number
  totalPoint: number
  promotions: unknown[]
}

/**
 * 订单的对公转账信息（008 / `AppOrderOfflinePayRespVO`）。
 *
 * ⚠️ **不是对公单时整个对象是 `null`**（不是空对象）—— 与 006 的 `invoice` 同一个做法。
 */
export interface OfflinePayInfo {
  /**
   * 是否还在**等客户打款**（= 是对公单 **且** 订单还没付）。
   *
   * ⚠️ 前端 MUST 用它**压过**"未支付 + 没有支付单 → 无需支付"那条既有判断（R47）——
   * 对公单正好命中那条判断，不压的话会把"要打款"显示成"无需支付"。
   */
  waiting: boolean
  /** 收款户名 / 账号 / 开户行 —— 由业务所有者在交易配置里填，前端不编造 */
  accountName?: string
  accountNo?: string
  bankName?: string
  tip?: string
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
   * 开票状态（006 / FR-178）：**没申请开票时为 `null`** —— 列表上什么都不显示。
   * 取值同订单详情：0 待开票 / 10 已开票 / 20 已作废。
   */
  invoiceStatus?: number | null
  /**
   * 是不是**还没确认收款的对公转账单**（008 / FR-200）。
   *
   * ⚠️ 列表上用它把状态显示成「**待收款**」而不是「待支付」，**并去掉「立即支付」入口** ——
   * 对公单在客户侧没有任何在线支付入口（FR-201）。后端一次批量查出来，前端不自己判。
   */
  offlineWaiting?: boolean
  /**
   * 发货物流单号；未发货 / 虚拟商品 / 自提为 null（010 / FR-273）。
   *
   * ⚠️ 列表上的「查看物流」入口**只认这个字段**，不要用订单状态代替：
   * 历史导入单里存在「已完成但没有运单号」的订单。
   */
  logisticsNo?: string | null
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
/**
 * 一条物流轨迹（010）。
 *
 * ⚠️ `time` 与订单其他时间一样是 **epoch 毫秒数**，渲染一律走 `utils/time.ts` 的
 * `formatDateTime`（FR-270）。
 */
export interface ExpressTrack {
  time: number | string
  content: string
}

export interface OrderDetail extends OrderPageItem, OrderPrice {
  /** epoch 毫秒数（见 `createTime` 的说明） */
  payExpireTime: number | string
  /** 发货物流公司名称（后端 `AppTradeOrderDetailRespVO.logisticsName`） */
  logisticsName?: string | null
  /** 发货时间，epoch 毫秒数 */
  deliveryTime?: number | string | null
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
  /**
   * 买家留言（下单时客户自己填的）。
   *
   * ⚠️ **与后台的「商家备注」不是一回事**：这一栏由**客户**填、客户在订单详情能看到；
   * 商家备注是运营在后台写的。两个字段在库/响应里分别是 `user_remark` / `remark`。
   * 没填时后端返回 `null`。
   */
  userRemark?: string | null
  receiverName: string
  receiverMobile: string
  receiverAreaName?: string
  receiverDetailAddress: string
  couponId?: number
  couponPrice: number
  pointPrice: number
  vipPrice: number
  /**
   * 开票信息（006）。**没申请开票时为 `null`**（不是空对象）——
   * 订单详情的发票区块四态靠它区分（FR-176）。
   */
  invoice?: OrderInvoice | null
  /**
   * 对公转账信息（008）。**不是对公单时为 `null`**（不是空对象）——
   * 「待收款」区块靠它决定渲不渲染，同时它也是"压过无需支付误判"的依据（R47）。
   */
  offlinePay?: OfflinePayInfo | null
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
  /**
   * 支付渠道（008）。**下单时只传 `offline`（对公转账）**，不传 = 在线支付（FR-211）。
   *
   * ⚠️ 别的渠道（`wx_lite` / `alipay_pc` …）是**支付时**才选的 —— 下单这一刻客户只有
   * "要不要走线下打款"这一个决定，传了别的值后端会拒。
   */
  payChannelCode?: string
  remark?: string
  /**
   * 开票信息（006 / FR-153~156）。**不传 = 不开票**。
   *
   * ⚠️ MUST 用 `undefined` 而不是 `null` / 空对象：后端把"有对象"当成"要开票"，
   * 空对象会落一条字段全空的申请（并被校验拒掉，报的是"缺哪一项"）。
   *
   * ⚠️ 它与订单**同一事务**落库（Q32）：缺项 / 税号非法会**拒绝整单**，
   * 客户改完再提交（这与"开票失败不该挡下单"不冲突 —— 那是输入错误，不是系统故障）。
   */
  invoice?: InvoiceApplyForm
}

/** 下单响应。**payPrice 为 0 的订单 `payOrderId` 是 null**，前端必须处理。 */
export interface OrderCreateResp {
  id: number
  payOrderId: number | null
}
