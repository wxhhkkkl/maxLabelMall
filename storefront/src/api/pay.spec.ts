import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: vi.fn(),
  del: vi.fn(),
}))

const { MOCK_CHANNEL_CODE, getPayOrder, submitPay } = await import('./pay')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

/**
 * 支付接口（`/app-api/pay/order/**`）。
 *
 * ⚠️ 两个必须钉住的点：
 *
 * 1. **body 的字段名是 `id`，值是下单返回的 `payOrderId`** —— 不是交易订单号。
 *    两者是不同的 id（`createOrder` 的响应里 `id` 是交易订单、`payOrderId` 是支付单）。
 *    传错会「找不到支付单」。
 * 2. **渠道固定 mock**，不涉及任何第三方商户号（SC-010）。
 */
describe('提交支付', () => {
  it('用 mock 通道提交，body 是 { id: 支付单号, channelCode }', async () => {
    post.mockResolvedValue(true)
    await submitPay(8899)
    expect(post).toHaveBeenCalledWith('/pay/order/submit', {
      id: 8899,
      channelCode: MOCK_CHANNEL_CODE,
    })
  })

  it('渠道码就是 mock —— 本期不接任何真实商户号（SC-010）', () => {
    expect(MOCK_CHANNEL_CODE).toBe('mock')
  })
})

describe('查询支付单', () => {
  it('按 id 查，可要求同步', async () => {
    get.mockResolvedValue({ id: 8899, status: 10 })
    await getPayOrder(8899, true)
    expect(get).toHaveBeenCalledWith('/pay/order/get', { id: 8899, sync: true })
  })

  it('默认不同步', async () => {
    get.mockResolvedValue({ id: 8899, status: 10 })
    await getPayOrder(8899)
    expect(get).toHaveBeenCalledWith('/pay/order/get', { id: 8899 })
  })
})
