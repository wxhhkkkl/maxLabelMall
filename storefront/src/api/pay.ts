import { get, post } from '@/config/http'
import type { PayOrder, PayOrderSubmitResp, PaySubmitReq } from '@/types'

/**
 * 支付接口（`/app-api/pay/order/**`、`/app-api/pay/channel/**`）。
 *
 * ⚠️ `id` 是**支付单号 `payOrderId`**，不是交易订单号。`createOrder` 的响应里两者
 * 并存（`id` 是交易订单、`payOrderId` 是支付单），传错会「找不到支付单」。
 *
 * ⚠️ **`payOrderId` 可能为 null**：`payPrice === 0` 的订单（例如一张券把订单全额
 * 抵扣）后端**不会**创建支付单 —— `createPayOrder` 是 `payOrderId` 的唯一写入点，
 * 而它被 `if (order.getPayPrice() > 0)` 包着。这种情况没有任何 id 可以提交，
 * 调用方 MUST 先判断，**不得把 null 传给本模块**（FR-037 / FR-038）。
 */

/**
 * 提交支付。
 *
 * `channelCode` **由调用方传入** —— 不再像上一版那样写死成 `mock`
 * （当时的规格约束 SC-010 是「本期不接第三方商户号」，已随接入支付宝/微信一并去掉）。
 * 渠道码的可用性来自 {@link listEnabledChannelCodes}，界面只负责让用户选。
 *
 * `channelExtras` 目前只有**微信公众号 JSAPI（`wx_pub`）**需要，传 `{ openid }` ——
 * 后端 `WxPubPayClient` 拿不到 openid 会直接拒。其余渠道不传，
 * 且**不传时不能把 `channelExtras: undefined` 塞进 body**。
 *
 * `returnUrl` 是**跳转型渠道**（如支付宝电脑网站支付）付完之后把浏览器送回哪一页 ——
 * 不传的话用户会停在渠道自己的页面上。同样**不传时不能把该键塞进 body**。
 *
 * ⚠️ 支付**不是**提交即成功：真实渠道要跳收银台、模拟通道也要等后端**异步回调**
 * `/app-api/trade/order/update-paid` 才把交易订单推进到「待发货」。所以提交成功后
 * 必须重新拉取订单详情来确认状态，**不得由前端把订单标记为已支付**（FR-039）。
 */
export function submitPay(
  payOrderId: number,
  channelCode: string,
  channelExtras?: Record<string, string>,
  returnUrl?: string,
): Promise<PayOrderSubmitResp> {
  const body: PaySubmitReq = { id: payOrderId, channelCode }
  if (channelExtras) {
    body.channelExtras = channelExtras
  }
  if (returnUrl) {
    body.returnUrl = returnUrl
  }
  return post<PayOrderSubmitResp>('/pay/order/submit', body)
}

/**
 * 查询支付单。`sync=true` 会主动同步渠道状态。
 *
 * 返回里带 `appId` —— 界面靠它去查「这个应用启用了哪些渠道」，
 * 免得前端自己记一个租户相关的应用编号（换个租户就错）。
 */
export function getPayOrder(id: number, sync = false): Promise<PayOrder> {
  return get<PayOrder>('/pay/order/get', sync ? { id, sync: true } : { id })
}

/**
 * 获得指定支付应用**已启用**的渠道编码列表。
 *
 * 后端接口：`/app-api/pay/channel/get-enable-code-list`。
 * 失败或返回空时回落成空数组 —— 界面据此降级为「暂无可用渠道」，
 * 而不是抛错把整个订单详情页带崩。
 */
export async function listEnabledChannelCodes(appId: number): Promise<string[]> {
  const codes = await get<string[]>('/pay/channel/get-enable-code-list', { appId })
  return Array.isArray(codes) ? codes : []
}
