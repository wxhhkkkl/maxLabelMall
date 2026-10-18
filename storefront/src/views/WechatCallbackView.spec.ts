import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

/**
 * 微信授权的**回跳页**（跑在手机上）—— T36x 的收尾。
 *
 * 它测两件事：把 `code`/`state` 交给服务端；以及**收尾时把这一页自己关掉** ——
 * 客户扫完码不该再自己点左上角退回微信。关不成的场合（不在微信里、桥拿不到）
 * 必须留着那句「回到电脑上继续」当兜底。
 */

const wechatScanCallback = vi.fn()
vi.mock('@/api/wechatLogin', () => ({
  wechatScanCallback: (...a: unknown[]) => wechatScanCallback(...a),
}))

const closeWeixinWindow = vi.fn()
vi.mock('@/utils/weixin', () => ({
  closeWeixinWindow: (...a: unknown[]) => closeWeixinWindow(...a),
}))

const WechatCallbackView = (await import('./WechatCallbackView.vue')).default

/**
 * **刻意不用 `@vue/test-utils` 的 `flushPromises`**：它内部走
 * `setImmediate ?? setTimeout`，而下面开了假定时器 —— 它会被永远挂住。
 */
async function mountAt(query: Record<string, string>) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/wechat/callback', component: { template: '<div/>' } }],
  })
  await router.push({ path: '/wechat/callback', query })
  await router.isReady()
  const w = mount(WechatCallbackView, { global: { plugins: [router] } })
  await vi.advanceTimersByTimeAsync(0)
  return w
}

beforeEach(() => {
  vi.useFakeTimers({
    toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'],
  })
  wechatScanCallback.mockReset()
  wechatScanCallback.mockResolvedValue(true)
  closeWeixinWindow.mockReset()
  closeWeixinWindow.mockResolvedValue(true)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('WechatCallbackView —— 手机端授权回跳', () => {
  it('拿到 code/state 就交给服务端，并告诉客户回电脑上继续', async () => {
    const w = await mountAt({ code: 'CODE-1', state: 'STATE-1' })

    expect(wechatScanCallback).toHaveBeenCalledWith('CODE-1', 'STATE-1')
    expect(w.text()).toContain('已确认')
    expect(w.text()).toContain('回到电脑上继续')
  })

  it('**约 1.2 秒后自己关掉这一页** —— 客户不必手动点左上角退回微信', async () => {
    await mountAt({ code: 'CODE-1', state: 'STATE-1' })
    // 先让客户看清「已确认」再关：闪关会让人以为没成
    await vi.advanceTimersByTimeAsync(1000)
    expect(closeWeixinWindow).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(200)
    expect(closeWeixinWindow).toHaveBeenCalledTimes(1)
  })

  it('**授权失败时不关** —— 客户得看见失败原因', async () => {
    wechatScanCallback.mockRejectedValue(new Error('授权已过期，请重新扫码'))
    const w = await mountAt({ code: 'CODE-1', state: 'STATE-1' })

    expect(w.text()).toContain('授权已过期，请重新扫码')
    await vi.advanceTimersByTimeAsync(10_000)
    expect(closeWeixinWindow).not.toHaveBeenCalled()
  })

  it('code/state 不全时不打接口也不关，只提示回电脑重扫', async () => {
    const w = await mountAt({ code: 'CODE-1' })

    expect(wechatScanCallback).not.toHaveBeenCalled()
    expect(w.text()).toContain('回到电脑上重新扫码')
    await vi.advanceTimersByTimeAsync(10_000)
    expect(closeWeixinWindow).not.toHaveBeenCalled()
  })

  it('客户在收尾前就离开（组件卸载）→ 定时器清掉，别再去关别的页', async () => {
    const w = await mountAt({ code: 'CODE-1', state: 'STATE-1' })
    w.unmount()

    await vi.advanceTimersByTimeAsync(5000)
    expect(closeWeixinWindow).not.toHaveBeenCalled()
  })
})
