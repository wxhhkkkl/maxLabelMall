import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import HeaderCart from './HeaderCart.vue'

const listCart = vi.hoisted(() => vi.fn())
const user = reactive({ isLogin: false })
const cart = reactive({ count: 0 })
vi.mock('@/api/cart', () => ({ listCart }))
vi.mock('@/store/user', () => ({ useUserStore: () => user }))
vi.mock('@/store/cart', () => ({ useCartStore: () => cart }))
enableAutoUnmount(afterEach)
afterEach(() => vi.useRealTimers())
beforeEach(() => { user.isLogin = false; cart.count = 0; listCart.mockReset() })
function create() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }, { path: '/cart', component: { template: '<div />' } }] })
  return mount(HeaderCart, { global: { plugins: [router] } })
}
const item = { id: 1, count: 3, spu: { id: 10, name: '热敏标签', picUrl: '' }, sku: { price: 1699, properties: [{ valueName: '50×30mm' }] } }
describe('顶部购物车预览', () => {
  it('未登录不查询私有接口，并保留购物车入口', async () => {
    const w = create()
    await w.get('.header-cart').trigger('mouseenter')
    expect(listCart).not.toHaveBeenCalled()
    expect(w.text()).toContain('登录后查看你的购物车')
    expect(w.get('.cart-preview-action').attributes('href')).toBe('/cart')
  })
  it('显示真实商品、规格、数量和有效商品小计，不把失效商品计入金额', async () => {
    user.isLogin = true
    listCart.mockResolvedValue({ validList: [item], invalidList: [{ ...item, id: 2 }] })
    const w = create()
    await w.get('.header-cart').trigger('mouseenter')
    await flushPromises()
    expect(w.text()).toContain('热敏标签')
    expect(w.text()).toContain('50×30mm')
    expect(w.get('.cart-preview-total').text()).toContain('50.97')
    expect(w.text()).toContain('1 项失效商品')
    expect(w.get('.cart-preview-item').attributes('href')).toBe('/product/10')
  })
  it('空购物车显示引导，接口失败允许重试', async () => {
    user.isLogin = true
    listCart.mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ validList: [], invalidList: [] })
    const w = create()
    await w.get('.header-cart').trigger('mouseenter')
    await flushPromises()
    expect(w.text()).toContain('暂时加载失败')
    await w.get('.cart-preview-state button').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('购物车还是空的')
  })
  it('移入面板取消关闭计时，离开后关闭，Escape 可关闭', async () => {
    vi.useFakeTimers()
    const w = create()
    await w.get('.header-cart').trigger('mouseenter')
    await w.get('.header-cart').trigger('mouseleave')
    await w.get('.header-cart-preview').trigger('mouseenter')
    await vi.advanceTimersByTimeAsync(400)
    expect(w.find('.header-cart-preview').exists()).toBe(true)
    await w.get('.header-cart').trigger('mouseleave')
    await vi.advanceTimersByTimeAsync(300)
    expect(w.find('.header-cart-preview').exists()).toBe(false)
    await w.get('.header-cart').trigger('focusin')
    await w.get('.header-cart').trigger('keydown', { key: 'Escape' })
    expect(w.find('.header-cart-preview').exists()).toBe(false)
  })
  it('退出登录后丢弃仍在途的购物车响应', async () => {
    user.isLogin = true
    let resolve!: (value: unknown) => void
    listCart.mockImplementation(() => new Promise(r => { resolve = r }))
    const w = create()
    await w.get('.header-cart').trigger('mouseenter')
    user.isLogin = false
    await flushPromises()
    resolve({ validList: [item], invalidList: [] })
    await flushPromises()
    expect(w.text()).not.toContain('热敏标签')
    expect(w.text()).toContain('登录后查看你的购物车')
  })
})
