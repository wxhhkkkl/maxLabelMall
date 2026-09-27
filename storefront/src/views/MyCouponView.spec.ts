import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Coupon } from '@/types'


const pageMyCoupons = vi.fn()
vi.mock('@/api/coupon', () => ({
  pageMyCoupons: (...a: unknown[]) => pageMyCoupons(...a),
  getUnusedCouponCount: vi.fn(),
  listCouponTemplates: vi.fn(),
  takeCoupon: vi.fn(),
}))

const MyCouponView = (await import('./MyCouponView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
    { path: '/coupon', component: { template: '<div/>' } },
    { path: '/coupon/mine', component: { template: '<div/>' } },
    { path: '/product/:id', component: { template: '<div/>' } },
  ],
})

function coupon(over: Partial<Coupon> = {}): Coupon {
  return {
    id: 1,
    name: '新人首单券',
    status: 1,
    usePrice: 10000,
    productScope: 1,
    productScopeValues: [],
    validStartTime: '2026-09-01 00:00:00',
    validEndTime: '2026-12-31 23:59:59',
    discountType: 1,
    discountPercent: 0,
    discountPrice: 3000,
    discountLimitPrice: 0,
    ...over,
  }
}

async function mountView() {
  const w = mount(MyCouponView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  pageMyCoupons.mockReset()
  pageMyCoupons.mockResolvedValue({ list: [coupon()], total: 1 })
  await router.push('/coupon/mine')
  await router.isReady()
})

describe('MyCouponView —— 分组与展示（FR-026d / design-new-pages §3.8）', () => {
  it('默认看「未使用」，按 status=1 拉取', async () => {
    const w = await mountView()
    expect(pageMyCoupons).toHaveBeenCalledWith(expect.objectContaining({ status: 1 }))
    expect(w.text()).toContain('新人首单券')
    expect(w.text()).toContain('¥30.00')
    expect(w.text()).toContain('满¥100.00可用')
  })

  it('三个分组标签齐全，且切换后按对应状态拉取', async () => {
    const w = await mountView()
    for (const label of ['未使用', '已使用', '已过期']) {
      expect(w.text()).toContain(label)
    }

    await w.findAll('.cat-pills .tab')[1].trigger('click') // 已使用
    await flushPromises()
    expect(pageMyCoupons).toHaveBeenLastCalledWith(expect.objectContaining({ status: 2 }))

    await w.findAll('.cat-pills .tab')[2].trigger('click') // 已过期
    await flushPromises()
    expect(pageMyCoupons).toHaveBeenLastCalledWith(expect.objectContaining({ status: 3 }))
  })

  it('当前分组为空时给空状态并引导去领券中心', async () => {
    pageMyCoupons.mockResolvedValue({ list: [], total: 0 })
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('领券中心')
  })

  it('加载失败给可重试的错误态（FR-045）', async () => {
    pageMyCoupons.mockRejectedValue(new Error('Network Error'))
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('重新加载')
  })
})

describe('MyCouponView —— 去使用（FR-026d）', () => {
  it('**未使用**的券给出「去使用」，指向商品', async () => {
    const w = await mountView()
    expect(w.find('.mc-use').exists()).toBe(true)
    expect(w.get('.mc-use').attributes('href')).toBe('/mall')
  })

  it('限定到**单个商品**的券直接跳到该商品详情页', async () => {
    pageMyCoupons.mockResolvedValue({
      list: [coupon({ productScope: 2, productScopeValues: [77] })],
      total: 1,
    })
    const w = await mountView()
    expect(w.get('.mc-use').attributes('href')).toBe('/product/77')
  })

  it('**已使用/已过期不给出「去使用」**（没有什么可用）', async () => {
    pageMyCoupons.mockResolvedValue({ list: [coupon({ status: 2 })], total: 1 })
    const w = await mountView()
    expect(w.find('.mc-use').exists()).toBe(false)

    pageMyCoupons.mockResolvedValue({ list: [coupon({ status: 3 })], total: 1 })
    const w2 = await mountView()
    expect(w2.find('.mc-use').exists()).toBe(false)
  })

  it('灰态券的状态文案与后端 status 对应', async () => {
    // 未使用的券给的是「去使用」入口而不是状态标签，所以这里看已使用/已过期
    pageMyCoupons.mockResolvedValue({ list: [coupon({ status: 2 })], total: 1 })
    const a = await mountView()
    expect(a.get('.mc-status').text()).toBe('已使用')

    pageMyCoupons.mockResolvedValue({ list: [coupon({ status: 3 })], total: 1 })
    const b = await mountView()
    expect(b.get('.mc-status').text()).toBe('已过期')
  })
})
