import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: vi.fn(),
  del: vi.fn(),
}))

const { getPayOrder, listEnabledChannelCodes, submitPay } = await import('./pay')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

/**
 * 支付接口（`/app-api/pay/order/**`、`/app-api/pay/channel/**`）。
 *
 * ⚠️ 三个必须钉住的点：
 *
 * 1. **body 的字段名是 `id`，值是下单返回的 `payOrderId`** —— 不是交易订单号。
 *    两者是不同的 id（`createOrder` 的响应里 `id` 是交易订单、`payOrderId` 是支付单）。
 *    传错会「找不到支付单」。
 * 2. **渠道码由调用方传入**。上一版写死 `mock`（SC-010），现在线上要接支付宝/微信，
 *    那个常量已删除 —— 渠道码是参数，不再有默认值。
 * 3. **可用渠道必须问后端**（`/pay/channel/get-enable-code-list`），前端不得自己判断
 *    哪个渠道可用。
 * 4. **公众号 JSAPI（`wx_pub`）必须带 `channelExtras.openid`**，且没传时**不能**把
 *    `channelExtras: undefined` 塞进 body —— 其余渠道不需要这个键。
 */
describe('提交支付', () => {
  it('body 是 { id: 支付单号, channelCode: 调用方给的渠道码 }', async () => {
    post.mockResolvedValue(true)
    await submitPay(8899, 'alipay_pc')
    expect(post).toHaveBeenCalledWith('/pay/order/submit', {
      id: 8899,
      channelCode: 'alipay_pc',
    })
  })

  it('渠道码是必传参数 —— 漏传会拼出 channelCode: undefined，不能靠默认值兜底', async () => {
    post.mockResolvedValue(true)
    // @ts-expect-error 故意漏传，验证不会静默回落成某个默认渠道
    await submitPay(8899)
    expect(post).toHaveBeenCalledWith('/pay/order/submit', {
      id: 8899,
      channelCode: undefined,
    })
  })

  it('传了 channelExtras 时放进 body —— 公众号 JSAPI 靠它带 openid（缺了后端必拒）', async () => {
    post.mockResolvedValue(true)
    await submitPay(8899, 'wx_pub', { openid: 'o-1' })
    expect(post).toHaveBeenCalledWith('/pay/order/submit', {
      id: 8899,
      channelCode: 'wx_pub',
      channelExtras: { openid: 'o-1' },
    })
  })

  it('没传 channelExtras 时 body **不含该键** —— 不给支付宝之类的渠道发一个 undefined', async () => {
    post.mockResolvedValue(true)
    await submitPay(8899, 'alipay_pc')
    expect(post).toHaveBeenCalledWith('/pay/order/submit', {
      id: 8899,
      channelCode: 'alipay_pc',
    })
    // `toHaveBeenCalledWith` 对 `{a:1,b:undefined}` 是宽容的，所以单独钉一次键集
    expect(Object.keys(post.mock.calls[0][1] as object)).toEqual(['id', 'channelCode'])
  })

  it('传了 returnUrl 时放进 body —— 支付宝付完要靠它把浏览器送回订单页', async () => {
    post.mockResolvedValue(true)
    await submitPay(8899, 'alipay_pc', undefined, 'https://new.yuwangchenfa.com/order/762')
    expect(post).toHaveBeenCalledWith('/pay/order/submit', {
      id: 8899,
      channelCode: 'alipay_pc',
      returnUrl: 'https://new.yuwangchenfa.com/order/762',
    })
  })

  it('没传 returnUrl 时 body **不含该键**（与 channelExtras 同一口径）', async () => {
    post.mockResolvedValue(true)
    await submitPay(8899, 'alipay_pc', { openid: 'o-1' })
    const body = post.mock.calls[0][1] as Record<string, unknown>
    expect(Object.keys(body)).toEqual(['id', 'channelCode', 'channelExtras'])
    expect('returnUrl' in body).toBe(false)
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

  it('返回里带 appId —— 前端靠它去查该应用启用的渠道码，无需自己记应用编号', async () => {
    get.mockResolvedValue({ id: 8899, status: 0, appId: 10 })
    const po = await getPayOrder(8899)
    expect(po.appId).toBe(10)
  })
})

describe('查询渠道', () => {
  it('按 appId 查启用的渠道码列表', async () => {
    get.mockResolvedValue(['alipay_pc'])
    const codes = await listEnabledChannelCodes(10)
    expect(get).toHaveBeenCalledWith('/pay/channel/get-enable-code-list', { appId: 10 })
    expect(codes).toEqual(['alipay_pc'])
  })

  it('后端返回空/异常时给出空数组，不抛 —— 界面要能降级成「暂无可用渠道」', async () => {
    get.mockResolvedValue(undefined)
    expect(await listEnabledChannelCodes(10)).toEqual([])
  })
})
