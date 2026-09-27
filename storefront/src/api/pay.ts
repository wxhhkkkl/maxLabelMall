import { get, post } from '@/config/http'
import type { PayOrder, PayOrderSubmitResp } from '@/types'

/**
 * 支付接口（`/app-api/pay/order/**`）。
 *
 * ⚠️ `id` 是**支付单号 `payOrderId`**，不是交易订单号。`createOrder` 的响应里两者
 * 并存（`id` 是交易订单、`payOrderId` 是支付单），传错会「找不到支付单」。
 *
 * ⚠️ **`payOrderId` 可能为 null**：`payPrice === 0` 的订单（例如一张券把订单全额
 * 抵扣）后端**不会**创建支付单 —— `createPayOrder` 是 `payOrderId` 的唯一写入点，
 * 而它被 `if (order.getPayPrice() > 0)` 包着。这种情况没有任何 id 可以提交，
 * 调用方 MUST 先判断，**不得把 null 传给本模块**（FR-037 / FR-038）。
 */

/** 本期唯一的支付渠道：模拟通道。不接任何第三方商户号（SC-010） */
export const MOCK_CHANNEL_CODE = 'mock'

/**
 * 提交支付。
 *
 * 模拟通道下**提交即成功**，但交易订单的状态要等后端**异步回调**
 * `/app-api/trade/order/update-paid` 才推进到「待发货」。所以提交成功后必须
 * 重新拉取订单详情来确认状态，**不得由前端把订单标记为已支付**（FR-039）。
 */
export function submitPay(payOrderId: number): Promise<PayOrderSubmitResp> {
  return post<PayOrderSubmitResp>('/pay/order/submit', {
    id: payOrderId,
    channelCode: MOCK_CHANNEL_CODE,
  })
}

/** 查询支付单。`sync=true` 会主动同步渠道状态 */
export function getPayOrder(id: number, sync = false): Promise<PayOrder> {
  return get<PayOrder>('/pay/order/get', sync ? { id, sync: true } : { id })
}
