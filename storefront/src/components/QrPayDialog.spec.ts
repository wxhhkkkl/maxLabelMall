import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { PayOrderStatus } from '@/types'

import QrPayDialog from './QrPayDialog.vue'

/**
 * 电脑端微信 Native 扫码支付弹层的行为契约。
 *
 * 它自己管轮询，所以这一组是整个功能里最容易悄悄坏掉的部分 —— 每条用例都针对
 * 一种「安静地不工作」：轮询不停（打微信打到天荒地老）、轮询不启动（用户扫完
 * 页面永远不刷新）、过期二维码赖在屏幕上、一次网络抖动掐死整条支付。
 *
 * ⚠️ 轮询用的 `GET /pay/order/get?sync=true` **每次都会真的去查一次微信**，
 * 所以「并发叠加」不是性能问题而是对外部系统的骚扰 —— 用递归 setTimeout 而非
 * setInterval 正是为了这个，用例 3 钉住它。
 */
const getPayOrder = vi.fn()
vi.mock('@/api/pay', () => ({
  getPayOrder: (...args: unknown[]) => getPayOrder(...args),
}))

const CODE_URL = 'weixin://wxpay/bizpayurl?pr=abcdefg'
const PAY_ORDER_ID = 8899

/** 支付单的默认返回 —— 待支付 */
function waiting(over: Record<string, unknown> = {}) {
  return {
    id: PAY_ORDER_ID,
    no: 'P202610100001',
    status: PayOrderStatus.WAITING,
    price: 1,
    channelCode: 'wx_native',
    appId: 10,
    ...over,
  }
}

/**
 * 推进微任务 + 0ms 定时器。
 *
 * **刻意不用 @vue/test-utils 的 flushPromises**：它内部走 `setImmediate ?? setTimeout`，
 * 在假定时器下可能永远不 resolve。这里只排空微任务队列，与定时器是否被 mock 无关。
 */
async function settle() {
  for (let i = 0; i < 4; i++) {
    await vi.advanceTimersByTimeAsync(0)
    await nextTick()
  }
}

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(QrPayDialog, {
    props: { open: true, codeUrl: CODE_URL, payOrderId: PAY_ORDER_ID, ...props },
  })
}

beforeEach(() => {
  vi.useFakeTimers({
    // 刻意不含 setImmediate：留着真实实现，避免被工具链的 promise 调度依赖到
    toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'],
  })
  vi.setSystemTime(new Date('2026-10-10T10:00:00Z'))
  getPayOrder.mockReset()
  getPayOrder.mockResolvedValue(waiting())
})

afterEach(() => {
  vi.useRealTimers()
})

describe('QrPayDialog —— 渲染', () => {
  it('把 code_url 渲染成 data:image/svg+xml 的 <img>（不是 canvas —— jsdom 里 canvas 根本用不了）', async () => {
    const w = mountDialog()
    await settle()

    const img = w.get('.qr-img')
    // 前缀断言同时钉住「用的是 SVG 输出」：走 toDataURL 会变成 data:image/png 且直接抛错
    expect(img.attributes('src')).toMatch(/^data:image\/svg\+xml/)
    expect(img.attributes('src')).toContain(encodeURIComponent('<svg'))
  })

  it('code_url 为空时明确报错，不渲染空白二维码、也不去轮询', async () => {
    const w = mountDialog({ codeUrl: '' })
    await settle()

    expect(w.find('.qr-img').exists()).toBe(false)
    expect(w.get('.qr-invalid').text()).toBeTruthy()
    // 没有二维码可扫，轮询毫无意义，只会在后端留一堆无用请求
    expect(getPayOrder).not.toHaveBeenCalled()
  })

  it('open 为 false 时不渲染弹层', async () => {
    const w = mountDialog({ open: false })
    await settle()

    expect(w.find('.qr-img').exists()).toBe(false)
  })
})

describe('QrPayDialog —— 轮询', () => {
  it('打开时立刻查一次（订单可能已经是已支付，不能等满一个轮询周期）', async () => {
    mountDialog()
    await settle()

    expect(getPayOrder).toHaveBeenCalledTimes(1)
    expect(getPayOrder).toHaveBeenCalledWith(PAY_ORDER_ID, true)
  })

  it('每 3 秒查一次', async () => {
    mountDialog()
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(3000)
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(3000)
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(3)
  })

  it('上一次请求还没落地时不会并发下一次（递归排期，不是 setInterval）', async () => {
    // 首次查询永远不返回
    getPayOrder.mockReturnValue(new Promise(() => {}))
    mountDialog()
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)

    // 推进到远超一个周期 —— 若用了 setInterval，这里就会叠出第 2、3 个请求
    await vi.advanceTimersByTimeAsync(3000 * 3)
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)
  })

  it('查不到时（网络抖动）不停止轮询、不抛错，只提示重试', async () => {
    getPayOrder.mockRejectedValueOnce(new Error('boom'))
    const w = mountDialog()
    await settle()

    expect(w.get('.qr-retry').text()).toBeTruthy()

    // 下一个周期照常继续
    getPayOrder.mockResolvedValue(waiting())
    await vi.advanceTimersByTimeAsync(3000)
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(2)
  })
})

describe('QrPayDialog —— 停止条件', () => {
  it('支付单成功 → emit paid，并且不再继续查', async () => {
    getPayOrder.mockResolvedValue(waiting({ status: PayOrderStatus.SUCCESS }))
    const w = mountDialog()
    await settle()

    expect(w.emitted('paid')).toHaveLength(1)

    await vi.advanceTimersByTimeAsync(3000 * 3)
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)
  })

  it('支付单关闭 → emit expired，并且不再继续查', async () => {
    getPayOrder.mockResolvedValue(waiting({ status: PayOrderStatus.CLOSED }))
    const w = mountDialog()
    await settle()

    expect(w.emitted('expired')).toHaveLength(1)

    await vi.advanceTimersByTimeAsync(3000 * 3)
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)
  })

  it('有效期已过（expireAt 在过去）→ 直接过期，不轮询', async () => {
    const w = mountDialog({ expireAt: Date.now() - 1000 })
    await settle()

    expect(w.emitted('expired')).toHaveLength(1)
    expect(getPayOrder).not.toHaveBeenCalled()
  })

  it('轮询途中倒计时归零 → emit expired 并停止轮询', async () => {
    const w = mountDialog({ expireAt: Date.now() + 2000 })
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)
    expect(w.emitted('expired')).toBeUndefined()

    await vi.advanceTimersByTimeAsync(2000)
    await settle()

    expect(w.emitted('expired')).toHaveLength(1)
    expect(w.get('.qr-expired').text()).toBeTruthy()

    // 过期后不再查
    const before = getPayOrder.mock.calls.length
    await vi.advanceTimersByTimeAsync(3000 * 3)
    await settle()
    expect(getPayOrder.mock.calls.length).toBe(before)
  })

  it('弹层关闭后停止轮询', async () => {
    const w = mountDialog()
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)

    await w.setProps({ open: false })
    await settle()

    await vi.advanceTimersByTimeAsync(3000 * 3)
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)
  })

  it('组件卸载后停止轮询（否则路由切走了还在打微信）', async () => {
    const w = mountDialog()
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)

    w.unmount()
    await settle()

    await vi.advanceTimersByTimeAsync(3000 * 3)
    await settle()
    expect(getPayOrder).toHaveBeenCalledTimes(1)
  })
})
