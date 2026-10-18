import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { InvoiceTitleKind, type InvoiceTitle } from '@/types'

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
  type: 2, // 专票
  titleKind: InvoiceTitleKind.COMPANY,
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
  it('**按票种分两段**（Q36：普票 / 专票分开管理），各自有「新增」入口', async () => {
    const w = await mountView([
      { ...TITLE, id: 1, type: 1, titleName: '普票公司', defaultStatus: false },
      { ...TITLE, id: 2, type: 2, titleName: '专票公司', defaultStatus: true },
    ])

    const groups = w.findAll('[data-invoice-group]')
    expect(groups).toHaveLength(2)
    expect(groups[0]!.text()).toContain('普票公司')
    expect(groups[0]!.text()).not.toContain('专票公司') // 两段不混
    expect(groups[1]!.text()).toContain('专票公司')
    // 每段各有自己的"新增"（点它会把票种带进弹层）
    expect(w.findAll('[data-add-title]')).toHaveLength(2)
  })

  it('新增时把该段的票种带进弹层', async () => {
    const w = await mountView([])
    await w.get('[data-add-title="2"]').trigger('click')

    // 弹层里的票种被预选成专票 → 于是要求五项全填
    expect(w.find('#invoiceTitleAddress').exists()).toBe(true)
  })

  it('列出抬头与它的字段，默认那条有标记', async () => {
    const w = await mountView()
    expect(w.text()).toContain('南京路桥机械有限公司')
    expect(w.text()).toContain('91320104660690975E')
    expect(w.text()).toContain('默认')
  })

  it('一条都没有时：两段各自给出空态与「新增」入口（不是空白页）', async () => {
    const w = await mountView([])

    // 分段之后不再是整页 EmptyState：每一段都要说清"这一段还没有"并给出新增入口
    expect(w.findAll('[data-invoice-group]')).toHaveLength(2)
    expect(w.text()).toContain('还没有电子普通发票的抬头')
    expect(w.text()).toContain('还没有电子增值税专用发票的抬头')
    expect(w.findAll('[data-add-title]')).toHaveLength(2)
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
      type: 2,
      // ⚠️ 这里是**逐项列字段**提交的：漏了 `titleKind`，客户点一下"设为默认"就会被后端拒（Q38）
      titleKind: InvoiceTitleKind.COMPANY,
      titleName: '南京路桥机械有限公司',
      taxNo: '91320104660690975E',
      titleAddress: '南京市玄武区某某路 1 号',
      titlePhone: '18913984596',
      bankName: '工商银行南京分行 6222',
      defaultStatus: true,
    })
    expect(listInvoiceTitles).toHaveBeenCalledTimes(2)
  })

  it('**个人抬头落在普票那一段里**，并带「个人」标记（Q38）', async () => {
    const w = await mountView([
      { ...TITLE, id: 5, type: 1, titleKind: InvoiceTitleKind.PERSONAL, titleName: '张三', taxNo: null, defaultStatus: false },
    ])

    const normalGroup = w.get('[data-invoice-group="1"]')
    expect(normalGroup.text()).toContain('张三')
    expect(normalGroup.text()).toContain('个人')
    // 专票那一段是空的 —— 个人抬头**不可能**出现在里面（个人开不了专票）
    expect(w.get('[data-invoice-group="2"]').text()).toContain('还没有电子增值税专用发票的抬头')
  })

  it('个人抬头设为默认：请求体同样带上 `titleKind`', async () => {
    const personal: InvoiceTitle = {
      id: 5, type: 1, titleKind: InvoiceTitleKind.PERSONAL, titleName: '张三', taxNo: null, defaultStatus: false,
    }
    const w = await mountView([personal])
    listInvoiceTitles.mockResolvedValue([{ ...personal, defaultStatus: true }])

    const setDefault = w.findAll('.invoice-title-actions .btn-cart').find(b => b.text() === '设为默认')
    await setDefault?.trigger('click')
    await flushPromises()

    expect(updateInvoiceTitle).toHaveBeenCalledWith(5, expect.objectContaining({
      type: 1,
      titleKind: InvoiceTitleKind.PERSONAL,
      titleName: '张三',
      defaultStatus: true,
    }))
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
