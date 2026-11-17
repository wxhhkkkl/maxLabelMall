import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

/**
 * 维修支付弹窗（021 / US73 / T067）
 *
 * 钉两件事：非微信环境不提交 wx_pub；普通渠道按既有 pay API 提交。
 */
const getPayOrder = vi.fn()
const listEnabledChannelCodes = vi.fn()
const submitPay = vi.fn()
vi.mock('@/api/pay', () => ({
  getPayOrder: (...a: unknown[]) => getPayOrder(...a),
  listEnabledChannelCodes: (...a: unknown[]) => listEnabledChannelCodes(...a),
  submitPay: (...a: unknown[]) => submitPay(...a)
}))
vi.mock('@/api/social', () => ({
  getSocialUser: vi.fn().mockResolvedValue(null),
  getSocialAuthRedirectUrl: vi.fn(),
  bindSocialUser: vi.fn()
}))

const Dialog = (await import('./MemberPayDialog.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/account/customer-service', component: { template: '<div/>' } }]
})

async function mountDialog() {
  const w = mount(Dialog, { global: { plugins: [router] } })
  ;(w.vm as unknown as { open: (id: number) => void }).open(99001)
  await flushPromises()
  return w
}

beforeEach(() => {
  getPayOrder.mockReset()
  getPayOrder.mockResolvedValue({ appId: 1, status: 10 })
  listEnabledChannelCodes.mockReset()
  submitPay.mockReset()
  submitPay.mockResolvedValue({ displayMode: null, status: 10 })
})

describe('维修支付弹窗', () => {
  it('非微信环境下 wx_pub 不提交支付', async () => {
    listEnabledChannelCodes.mockResolvedValue(['wx_pub'])
    const w = await mountDialog()

    await w.findAll('button').find((b) => b.text() === '微信支付')!.trigger('click')
    await w.findAll('button').find((b) => b.text() === '立即支付')!.trigger('click')
    await flushPromises()

    expect(submitPay).not.toHaveBeenCalled()
  })

  it('普通渠道按 pay API 提交', async () => {
    listEnabledChannelCodes.mockResolvedValue(['alipay_pc'])
    const w = await mountDialog()

    await w.findAll('button').find((b) => b.text() === '立即支付')!.trigger('click')
    await flushPromises()

    expect(submitPay).toHaveBeenCalledWith(99001, 'alipay_pc', undefined, window.location.href)
  })
})
