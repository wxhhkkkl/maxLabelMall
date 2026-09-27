import { del, get, post } from '@/config/http'
import type {
  OrderCreateReq,
  OrderCreateResp,
  OrderDetail,
  OrderPageItem,
  PageResult,
  SettlementResp,
} from '@/types'

/**
 * 交易接口（`/app-api/trade/order/**`），全部需登录。
 *
 * 三个必须遵守的契约细节（见 contracts/app-api.md）：
 *
 * 1. **结算的数组参数要手工拼 query**：SpringMVC 需要 `items[0].skuId=..` 这种带
 *    下标的字面形式，不能依赖默认的对象序列化。既有的 C 端工程也是手工拼的。
 *
 * 2. **`pointStatus` 是必填**（后端 `@NotNull`）。本期不展示积分，恒传 `false`；
 *    漏传会得到「是否使用积分不能为空」。
 *
 * 3. **取消订单是 `DELETE`**（后端 `@DeleteMapping`），且 `id` 走 query 参数。
 *    用 PUT 会拿到 405。
 */

/** 本期固定按快递配送（FR-027a，不提供配送方式切换与自提） */
export const DELIVERY_TYPE_EXPRESS = 1

/**
 * 结算/下单的**入参**：`pointStatus` 与 `deliveryType` 不在其中。
 *
 * 这两个字段在协议上是必填的（`pointStatus` 后端标了 `@NotNull`），但本期它们的
 * 取值是**固定**的（不使用积分、按快递）。把它们留在调用方手里，等于让每个页面
 * 都承担一次"记得传对"的责任 —— 漏传就报「是否使用积分不能为空」。
 * 因此由本模块统一兜住，调用方只需给商品、地址与券。
 */
export type TradeOrderInput = Omit<OrderCreateReq, 'pointStatus' | 'deliveryType'> & {
  /** 仅测试与未来的多配送方式需要覆盖；业务代码不要传 */
  pointStatus?: boolean
  deliveryType?: number
}

/**
 * 把结算/下单请求拼成 query 串。
 *
 * ⚠️ **下标里的方括号要百分号编码成 `%5B` / `%5D`。**
 * `[` `]` 不是 RFC 7230/3986 的合法 query 字符，而 yudao 后端没有配
 * `server.tomcat.relaxed-query-chars`（本项目也不许改后端）。字面写
 * `items[0].skuId` 会被 Tomcat **直接拒收**，回一张 HTML 的 400 页面 ——
 * 前端只看到"结算失败"，很容易误判成业务错误。编码后 SpringMVC 仍会把它
 * 解码回 `items[0].skuId` 并正确绑定（已实测）。
 */
export function buildSettlementQuery(req: TradeOrderInput): string {
  const parts: string[] = []

  req.items.forEach((item, i) => {
    const key = `items%5B${i}%5D`
    parts.push(`${key}.skuId=${encodeURIComponent(item.skuId)}`)
    parts.push(`${key}.count=${encodeURIComponent(item.count)}`)
    if (item.cartId !== undefined) {
      parts.push(`${key}.cartId=${encodeURIComponent(item.cartId)}`)
    }
  })

  if (req.couponId !== undefined) parts.push(`couponId=${encodeURIComponent(req.couponId)}`)
  if (req.addressId !== undefined) parts.push(`addressId=${encodeURIComponent(req.addressId)}`)
  // 必填：本期固定不使用积分
  parts.push(`pointStatus=${req.pointStatus === true}`)
  // 本期固定按快递
  parts.push(`deliveryType=${req.deliveryType ?? DELIVERY_TYPE_EXPRESS}`)

  return parts.join('&')
}

/**
 * 结算：GET，参数在 query 上。
 *
 * **购物车与商品页直购走的是同一个端点** —— 后端的 `calculatePrice` 对「skuId + count」
 * 与「cartId」两种入参都支持（见 TradeOrderConvert 的「情况一 / 情况二」）。
 *
 * 早期版本把直购拆到 `/trade/order/settlement-product`，那是**契约误读**：该端点只收
 * `spuIds: List<Long>`、标着 `@PermitAll`、返回的是「商品列表 / 详情用的活动价格信息」
 * （`List<AppTradeProductSettlementRespVO>`），与结算下单无关，调用必然拿不到 items。
 */
export function settlement(req: TradeOrderInput): Promise<SettlementResp> {
  return get<SettlementResp>(`/trade/order/settlement?${buildSettlementQuery(req)}`)
}

/**
 * 下单。body 用 JSON（不像结算那样拼 query）。
 * 响应里的 `payOrderId` **可能是 null** —— 全额券抵扣的订单没有支付单。
 */
export function createOrder(req: TradeOrderInput): Promise<OrderCreateResp> {
  return post<OrderCreateResp>('/trade/order/create', {
    items: req.items,
    couponId: req.couponId,
    addressId: req.addressId,
    pointStatus: req.pointStatus === true,
    deliveryType: req.deliveryType ?? DELIVERY_TYPE_EXPRESS,
    remark: req.remark,
  })
}

/** 我的订单分页；`status` 为空表示全部 */
export function pageOrders(params: {
  pageNo?: number
  pageSize?: number
  status?: number
}): Promise<PageResult<OrderPageItem>> {
  return get<PageResult<OrderPageItem>>('/trade/order/page', params)
}

/** 订单详情。`sync=true` 会主动同步支付状态（支付后确认状态用） */
export function getOrderDetail(id: number, sync = false): Promise<OrderDetail> {
  return get<OrderDetail>('/trade/order/get-detail', sync ? { id, sync: true } : { id })
}

/** 取消订单。**DELETE，且 id 走 query**（仅待支付可用） */
export function cancelOrder(id: number): Promise<boolean> {
  return del<boolean>('/trade/order/cancel', { id })
}

/** 各状态的订单数量（订单列表的分类标签） */
export function getOrderCount(): Promise<Record<string, number>> {
  return get<Record<string, number>>('/trade/order/get-count')
}
