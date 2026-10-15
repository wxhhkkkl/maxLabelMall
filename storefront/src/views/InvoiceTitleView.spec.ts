import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { InvoiceTitle } from '@/types'

const listInvoiceTitles = vi.fn()
const deleteInvoiceTitle = vi.fn()
const updateInvoiceTitle = vi.fn()
vi.mock('@/api/invoice', () => ({
  listInvoiceTitles: (...a: unknown[]) => listInvoiceTitles(...a),
  deleteInvoiceTitle: (...a: unknown[]) => deleteInvoiceTitle(...a),
  updateInvoiceTitle: (...a: unknown[]) => updateInvoiceTitle(...a),
  createInvoiceTitle: vi.fn(),
  createOrderInvoice: vi.fn(),
}))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' }),
  logout: vi.fn(),
}))
vi.mock('@/api/cart', () => ({ getCartCount: vi.fn().mockResolvedValue(0) }))

const InvoiceTitleView = (await import('./InvoiceTitleView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/account/invoice', component: { template: '<div/>' } },
    { path: '/account', component: { template: '<div/>' } },
    { path: '/', component: { template: '<div/>' } },
  ],
})

const TITLE: InvoiceTitle = {
  id: 3,
  titleName: '南京路桥机械有限公司',
  taxNo: '91320104660690975E',
  titleAddress: '南京市玄武区某某路 1 号',
  titlePhone: '18913984596',
  bankName: '工商银行南京分行 6222',
  defaultStatus: true,
}

async function mountView(list: InvoiceTitle[] = [TITLE]) {
  listInvoiceTitles.mockResolvedValue(list)
  const w = mount(InvoiceTitleView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
  listInvoiceTitles.mockReset()
  deleteInvoiceTitle.mockReset()
  deleteInvoiceTitle.mockResolvedValue(true)
  updateInvoiceTitle.mockReset()
  updateInvoiceTitle.mockResolvedValue(true)
  await router.push('/account/invoice')
  await router.isReady()
})

describe('发票抬头簿页面', () => {
  it('列出抬头与它的字段，默认那条有标记', async () => {
    const w = await mountView()
    expect(w.text()).toContain('南京路桥机械有限公司')
    expect(w.text()).toContain('91320104660690975E')
    expect(w.text()).toContain('默认')
  })

  it('没有抬头时给空态与下一步引导（不是空白页）', async () => {
    const w = await mountView([])
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.find('#addInvoiceTitle').exists()).toBe(true)
  })

  it('删除后重新拉列表（以服务端为准）', async () => {
    const w = await mountView()
    listInvoiceTitles.mockResolvedValue([])

    const del = w.findAll('.invoice-title-actions .btn-cart').find(b => b.text() === '删除')
    await del?.trigger('click')
    await flushPromises()

    expect(deleteInvoiceTitle).toHaveBeenCalledWith(3)
    expect(listInvoiceTitles).toHaveBeenCalledTimes(2)
  })

  it('设为默认：带上这条抬头的全部字段 + defaultStatus=true，并重新拉列表', async () => {
    const w = await mountView([{ ...TITLE, defaultStatus: false }])
    listInvoiceTitles.mockResolvedValue([{ ...TITLE }])

    const setDefault = w.findAll('.invoice-title-actions .btn-cart').find(b => b.text() === '设为默认')
    await setDefault?.trigger('click')
    await flushPromises()

    expect(updateInvoiceTitle).toHaveBeenCalledWith(3, {
      titleName: '南京路桥机械有限公司',
      taxNo: '91320104660690975E',
      titleAddress: '南京市玄武区某某路 1 号',
      titlePhone: '18913984596',
      bankName: '工商银行南京分行 6222',
      defaultStatus: true,
    })
    expect(listInvoiceTitles).toHaveBeenCalledTimes(2)
  })

  it('拉列表失败 → 错误态 + 可重试，而不是白屏', async () => {
    listInvoiceTitles.mockRejectedValue(new Error('offline'))
    const w = mount(InvoiceTitleView, { global: { plugins: [router, createPinia()] } })
    await flushPromises()

    expect(w.text()).toContain('发票抬头加载失败')
  })

  it('页面写明「改 / 删抬头不影响历史订单」（否则客户不敢删）', async () => {
    const w = await mountView()
    expect(w.text()).toContain('不会影响任何历史订单')
  })
})
