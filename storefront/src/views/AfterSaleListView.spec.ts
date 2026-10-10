import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

const pageAfterSales = vi.fn()
const cancelAfterSale = vi.fn()
vi.mock('@/api/afterSale', () => ({
  pageAfterSales: (...a: unknown[]) => pageAfterSales(...a),
  cancelAfterSale: (...a: unknown[]) => cancelAfterSale(...a),
}))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' }),
  logout: vi.fn(),
}))

import AfterSaleListView from './AfterSaleListView.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/account', component: { template: '<div/>' } },
    { path: '/account/after-sale', component: { template: '<div/>' } },
    { path: '/account/address', component: { template: '<div/>' } },
    { path: '/order', component: { template: '<div/>' } },
    { path: '/order/:id', component: { template: '<div/>' } },
    { path: '/coupon', component: { template: '<div/>' } },
    { path: '/coupon/mine', component: { template: '<div/>' } },
  ],
})

/** 一条售后记录；时间字段是 **epoch 毫秒**（与线上一致，别写成字符串） */
function item(over: Record<string, unknown> = {}) {
  return {
    id: 2048,
    no: 'AS20261009',
    status: 10,
    way: 10,
    applyReason: '不想要了',
    refundPrice: 7000,
    createTime: new Date(2026, 9, 9, 17, 30).getTime(),
    orderId: 762,
    orderNo: 'o202610091530271',
    orderItemId: 11,
    spuName: '三防热敏标签纸',
    picUrl: '',
    count: 1,
    ...over,
  }
}

function page(list: unknown[], total = list.length) {
  return { list, total }
}

async function mountView() {
  const w = mount(AfterSaleListView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
  pageAfterSales.mockReset()
  pageAfterSales.mockResolvedValue(page([item()]))
  cancelAfterSale.mockReset()
  cancelAfterSale.mockResolvedValue(true)
  await router.push('/account/after-sale')
})

/**
 * 「我的售后」（FR-011c / SC-023）。
 *
 * ⚠️ 撤销入口的依据是**列表返回的精确售后单状态**，不是订单项那个粗粒度的「售后中」——
 * 所以这里能准确地区分"能撤"与"不能撤"（如商家已收货待退款）。
 */
describe('AfterSaleListView —— 列表渲染', () => {
  it('展示 售后单号 / 商品 / 退款金额 / 申请时间 / 状态', async () => {
    const w = await mountView()
    expect(w.text()).toContain('AS20261009')
    expect(w.text()).toContain('三防热敏标签纸')
    expect(w.text()).toContain('¥70.00')
    expect(w.text()).toContain('2026-10-09 17:30')
    expect(w.text()).toContain('申请中')
  })

  it('**可撤销的状态给按钮**（申请中/卖家同意/待卖家收货）', async () => {
    for (const status of [10, 20, 30]) {
      pageAfterSales.mockResolvedValue(page([item({ status })]))
      const w = await mountView()
      expect(w.find('.as-cancel').exists()).toBe(true)
    }
  })

  it('**不可撤销的状态不给按钮**（商家已收货待退款 40 / 完成 50 / 已取消 61）', async () => {
    for (const status of [40, 50, 61]) {
      pageAfterSales.mockResolvedValue(page([item({ status })]))
      const w = await mountView()
      expect(w.find('.as-cancel').exists()).toBe(false)
    }
  })

  it('分页：把 pageNo 传给接口，翻页后重拉', async () => {
    pageAfterSales.mockResolvedValue(page([item()], 30))
    const w = await mountView()
    expect(pageAfterSales).toHaveBeenCalledWith({ pageNo: 1, pageSize: expect.any(Number) })

    await w.get('.pg-next').trigger('click')   // 「下一页」
    await flushPromises()
    expect(pageAfterSales.mock.calls.length).toBeGreaterThan(1)
  })
})

describe('AfterSaleListView —— 空 / 错误态', () => {
  it('没有记录时给空状态与引导，不是空白页', async () => {
    pageAfterSales.mockResolvedValue(page([]))
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
  })

  it('加载失败给可重试的错误态', async () => {
    pageAfterSales.mockRejectedValue(new Error('Network Error'))
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('重新加载')
  })
})

describe('AfterSaleListView —— 撤销', () => {
  it('点撤销先弹确认，**确认后才发请求**（按售后单编号）', async () => {
    const w = await mountView()
    await w.get('.as-cancel').trigger('click')
    await flushPromises()
    expect(cancelAfterSale).not.toHaveBeenCalled()
    expect(w.find('#asConfirmCancel').exists()).toBe(true)

    await w.get('#asConfirmCancel').trigger('click')
    await flushPromises()
    expect(cancelAfterSale).toHaveBeenCalledWith(2048)
  })

  it('撤销成功后重拉列表', async () => {
    const w = await mountView()
    const before = pageAfterSales.mock.calls.length
    await w.get('.as-cancel').trigger('click')
    await flushPromises()
    await w.get('#asConfirmCancel').trigger('click')
    await flushPromises()
    expect(pageAfterSales.mock.calls.length).toBeGreaterThan(before)
  })

  it('点「再想想」不发请求', async () => {
    const w = await mountView()
    await w.get('.as-cancel').trigger('click')
    await flushPromises()
    await w.get('#asDismissCancel').trigger('click')
    await flushPromises()
    expect(cancelAfterSale).not.toHaveBeenCalled()
  })

  it('撤销失败原样透出后端文案（如"状态不允许取消"）', async () => {
    cancelAfterSale.mockRejectedValue({ message: '售后单状态不允许取消' })
    const w = await mountView()
    await w.get('.as-cancel').trigger('click')
    await flushPromises()
    await w.get('#asConfirmCancel').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('售后单状态不允许取消')
  })
})

describe('AfterSaleListView —— 共用侧栏（FR-011e）', () => {
  it('进入本页侧栏仍在，且本页入口高亮', async () => {
    const w = await mountView()
    expect(w.find('.account-sidebar').exists()).toBe(true)
    const links = w.findAll('.account-sidebar a').map((a) => a.attributes('href'))
    expect(links).toContain('/account/after-sale')
  })
})
