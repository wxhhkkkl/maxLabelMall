import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 微信扫码登录面板的测试（T343）
 *
 * 重点**不是**画二维码（那是 `qrcode` 库的事），而是**轮询的三条停止条件**与**不叠请求** ——
 * 这两件事写错了不会报错，只会一直打后端，很难被发现（照 `QrPayDialog` 的既有经验）。
 */

const createWechatScanTicket = vi.fn()
const queryWechatScanTicket = vi.fn()
vi.mock('@/api/wechatLogin', () => ({
  createWechatScanTicket: (...a: unknown[]) => createWechatScanTicket(...a),
  queryWechatScanTicket: (...a: unknown[]) => queryWechatScanTicket(...a),
}))

const WechatQrPanel = (await import('./WechatQrPanel.vue')).default

/** 面板自己的轮询间隔，与组件保持一致 */
const POLL_MS = 3000

async function mountPanel() {
  const w = mount(WechatQrPanel, { props: { open: true } })
  await flushPromises()
  return w
}

beforeEach(() => {
  vi.useFakeTimers()
  createWechatScanTicket.mockReset()
  createWechatScanTicket.mockResolvedValue({
    ticket: 't-1',
    qrContent: 'https://open.weixin.qq.com/connect/oauth2/authorize?state=s-1',
    expiresIn: 300,
  })
  queryWechatScanTicket.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('WechatQrPanel —— 扫码登录的轮询（T343 / FR-144 / FR-140）', () => {
  it('打开时签发一次票据，并把 qrContent 渲染成二维码', async () => {
    const w = await mountPanel()

    expect(createWechatScanTicket).toHaveBeenCalledTimes(1)
    // qrContent 要渲染成图（SVG data URL），空的不算
    expect(w.get('#wxQrImage').attributes('src')).toMatch(/^data:image\/svg/)
  })

  it('**轮询到「已完成」就停**，并把令牌抛给上层', async () => {
    queryWechatScanTicket.mockResolvedValue({
      status: '已完成',
      accessToken: 'at-1',
      refreshToken: 'rt-1',
    })
    const w = await mountPanel()

    await vi.advanceTimersByTimeAsync(POLL_MS)
    await flushPromises()

    expect(w.emitted('loggedIn')).toEqual([['at-1', 'rt-1']])

    // ⚠️ 关键：完成之后 MUST NOT 再轮询（否则就是一直打后端）
    queryWechatScanTicket.mockClear()
    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(queryWechatScanTicket).not.toHaveBeenCalled()
  })

  it('**轮询到「已过期」就停**，并给出重新获取的入口', async () => {
    queryWechatScanTicket.mockResolvedValue({ status: '已过期' })
    const w = await mountPanel()

    await vi.advanceTimersByTimeAsync(POLL_MS)
    await flushPromises()

    expect(w.text()).toContain('已失效')
    expect(w.find('#wxQrRefresh').exists()).toBe(true)

    queryWechatScanTicket.mockClear()
    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(queryWechatScanTicket).not.toHaveBeenCalled()
  })

  it('**组件卸载即停**轮询（客户关掉弹层之后不该还在打后端）', async () => {
    queryWechatScanTicket.mockResolvedValue({ status: '待扫' })
    const w = await mountPanel()
    await vi.advanceTimersByTimeAsync(POLL_MS)
    await flushPromises()

    w.unmount()
    queryWechatScanTicket.mockClear()
    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(queryWechatScanTicket).not.toHaveBeenCalled()
  })

  it('**不叠请求**：上一次还没回来，就不再发下一次', async () => {
    // 一个**永远不 resolve** 的查询，模拟"后端变慢"
    queryWechatScanTicket.mockImplementation(() => new Promise(() => {}))
    await mountPanel()

    await vi.advanceTimersByTimeAsync(POLL_MS * 3)

    // 递归 setTimeout 的写法下，第一个请求没回来就不会排下一个 —— 所以只有 1 次。
    // 若写成 setInterval，这里会是 3 次（每次都在骚扰后端）
    expect(queryWechatScanTicket).toHaveBeenCalledTimes(1)
  })

  it('轮询到「待补全」时把票据交给上层，由补全页接手（US17）', async () => {
    queryWechatScanTicket.mockResolvedValue({ status: '待补全', wxNickname: '张三', wxAvatar: 'a.png' })
    const w = await mountPanel()

    await vi.advanceTimersByTimeAsync(POLL_MS)
    await flushPromises()

    expect(w.emitted('needProfile')).toEqual([['t-1', '张三', 'a.png']])
  })

  it('**「待补全」之后不许停轮询** —— 客户补完之后要靠它把令牌取回来', async () => {
    // ⚠️ 这条是**接线时才发现的 bug**（不是测试先抓到的，如实记录）：
    //    原先在「待补全」分支里也调了 stopAll()，于是客户在同一台电脑上填补全页、
    //    后端把票据标成「已完成」之后，**没有任何人在轮询**，令牌永远取不回来 ——
    //    表现是"点了完成之后什么都不发生"。
    queryWechatScanTicket
      .mockResolvedValueOnce({ status: '待补全', wxNickname: '张三' })
      .mockResolvedValueOnce({ status: '已完成', accessToken: 'at-1', refreshToken: 'rt-1' })
    const w = await mountPanel()

    await vi.advanceTimersByTimeAsync(POLL_MS)
    await flushPromises()
    expect(w.emitted('needProfile')).toEqual([['t-1', '张三', undefined]])

    await vi.advanceTimersByTimeAsync(POLL_MS)
    await flushPromises()
    expect(w.emitted('loggedIn')).toEqual([['at-1', 'rt-1']])
  })
})
