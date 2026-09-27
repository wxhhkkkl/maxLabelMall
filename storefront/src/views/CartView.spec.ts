import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { CartItem } from '@/types'

const listCart = vi.fn()
const updateCartCount = vi.fn()
const updateCartSelected = vi.fn()
const deleteCartItems = vi.fn()
const getCartCount = vi.fn()
vi.mock('@/api/cart', () => ({
  listCart: (...a: unknown[]) => listCart(...a),
  updateCartCount: (...a: unknown[]) => updateCartCount(...a),
  updateCartSelected: (...a: unknown[]) => updateCartSelected(...a),
  deleteCartItems: (...a: unknown[]) => deleteCartItems(...a),
  getCartCount: (...a: unknown[]) => getCartCount(...a),
  addToCart: vi.fn(),
}))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' }),
  logout: vi.fn(),
  login: vi.fn(),
  smsLogin: vi.fn(),
  sendSmsCode: vi.fn(),
  SMS_SCENE_MEMBER_LOGIN: 1,
}))

const CartView = (await import('./CartView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/cart', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
    { path: '/checkout', component: { template: '<div/>' } },
    { path: '/product/:id', component: { template: '<div/>' } },
  ],
})

function item(id: number, over: Partial<CartItem> = {}): CartItem {
  return {
    id,
    count: 1,
    selected: false,
    spu: { id: 10 + id, name: `真实商品 ${id}`, picUrl: '', price: 1290, stock: 99 },
    sku: {
      id: 100 + id,
      properties: [{ propertyId: 1, propertyName: '版本', valueId: id, valueName: `规格 ${id}` }],
      price: 1290,
      marketPrice: 0,
      stock: 99,
    },
    ...over,
  }
}

async function mountCart(valid: CartItem[], invalid: CartItem[] = []) {
  listCart.mockResolvedValue({ validList: valid, invalidList: invalid })
  getCartCount.mockResolvedValue(valid.length)
  const w = mount(CartView, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
  listCart.mockReset()
  updateCartCount.mockReset()
  updateCartSelected.mockReset()
  deleteCartItems.mockReset()
  getCartCount.mockReset()
  updateCartCount.mockResolvedValue(true)
  updateCartSelected.mockResolvedValue(true)
  deleteCartItems.mockResolvedValue(true)
})

describe('CartView —— 条目展示（FR-020）', () => {
  it('展示主图/名称/规格/单价/数量/小计', async () => {
    const w = await mountCart([item(1, { count: 2 })])
    expect(w.text()).toContain('真实商品 1')
    expect(w.text()).toContain('版本：规格 1')
    expect(w.text()).toContain('¥12.90')
    expect(w.get('.cart-qty-num').text()).toBe('2')
    expect(w.text()).toContain('¥25.80') // 小计 = 12.90 × 2
  })

  it('只对**已勾选**的条目计算合计', async () => {
    const w = await mountCart([
      item(1, { count: 2, selected: true }),
      item(2, { count: 1, selected: false }),
    ])
    expect(w.get('.cart-total').text()).toContain('¥25.80')
  })

  it('没有勾选时合计为 0，且去结算不可用', async () => {
    const w = await mountCart([item(1, { count: 2, selected: false })])
    expect(w.get('.cart-total').text()).toContain('¥0.00')
    expect(w.get('#toCheckout').attributes('disabled')).toBeDefined()
  })

  it('购物车为空时展示空状态与去商城的引导', async () => {
    const w = await mountCart([])
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('商城')
  })
})

describe('CartView —— 勾选与改量（后端持久化）', () => {
  it('点勾选会调用 update-selected（不是纯前端状态）', async () => {
    const w = await mountCart([item(1, { selected: false })])
    await w.get('.cart-check input').setValue(true)
    await flushPromises()
    expect(updateCartSelected).toHaveBeenCalledWith([1], true)
  })

  it('加数量调用 update-count', async () => {
    const w = await mountCart([item(1, { count: 1 })])
    await w.get('.cart-qty-plus').trigger('click')
    await flushPromises()
    expect(updateCartCount).toHaveBeenCalledWith(1, 2)
  })

  it('数量最低为 1（不会减到 0 或负数）', async () => {
    const w = await mountCart([item(1, { count: 1 })])
    await w.get('.cart-qty-minus').trigger('click')
    await flushPromises()
    expect(updateCartCount).not.toHaveBeenCalled()
  })

  it('删除条目调用 delete', async () => {
    const w = await mountCart([item(1)])
    await w.get('.cart-remove').trigger('click')
    await flushPromises()
    expect(deleteCartItems).toHaveBeenCalledWith([1])
  })
})

describe('CartView —— 失效条目由后端判定（FR-023）', () => {
  it('失效条目单独成区，明确标注不可购买', async () => {
    const w = await mountCart([item(1)], [item(9)])
    expect(w.find('.cart-invalid').exists()).toBe(true)
    expect(w.get('.cart-invalid').text()).toContain('真实商品 9')
    expect(w.get('.cart-invalid').text()).toContain('失效')
  })

  it('**失效条目不可被勾选**', async () => {
    const w = await mountCart([item(1)], [item(9, { selected: false })])
    // 失效区里不应有可用的勾选框
    const checks = w.findAll('.cart-invalid .cart-check input')
    expect(checks).toHaveLength(0)
  })

  it('**前端不自行判断下架与售罄** —— 后端说失效就是失效，即使库存看起来充足', async () => {
    const w = await mountCart([], [item(9, { sku: { ...item(9).sku, stock: 999 } })])
    expect(w.get('.cart-invalid').text()).toContain('失效')
  })

  it('有失效条目时仍可结算有效条目', async () => {
    const w = await mountCart([item(1, { selected: true })], [item(9)])
    expect(w.get('#toCheckout').attributes('disabled')).toBeUndefined()
  })
})

describe('CartView —— 库存上限以后端为准（FR-021）', () => {
  it('后端拒绝时给出提示并回退到原数量', async () => {
    updateCartCount.mockRejectedValue({ code: 400, message: '库存不足' })
    const w = await mountCart([item(1, { count: 1 })])
    await w.get('.cart-qty-plus').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('库存不足')
  })
})

describe('CartView —— 去结算只提交被勾选的条目（FR-025）', () => {
  it('勾选条目后按钮可用，点击进入结算页', async () => {
    const w = await mountCart([item(1, { selected: true }), item(2, { selected: false })])
    const btn = w.get('#toCheckout')
    expect(btn.attributes('disabled')).toBeUndefined()
    await btn.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/checkout')
  })

  it('**只把被勾选的条目 id 带给结算页**，未勾选的不进结算', async () => {
    const w = await mountCart([
      item(1, { selected: true }),
      item(2, { selected: false }),
      item(3, { selected: true }),
    ])
    await w.get('#toCheckout').trigger('click')
    await flushPromises()
    const q = router.currentRoute.value.query
    expect(q.source).toBe('cart')
    expect(q.cartIds).toBe('1,3')
  })
})
