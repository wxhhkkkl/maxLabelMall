import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { ProductSpu } from '@/types'

const addToCartMock = vi.fn()
vi.mock('@/api/cart', () => ({
  addToCart: (...a: unknown[]) => addToCartMock(...a),
  getCartCount: vi.fn().mockResolvedValue(0),
  listCart: vi.fn(),
  updateCartCount: vi.fn(),
  updateCartSelected: vi.fn(),
  deleteCartItems: vi.fn(),
}))

const pageProducts = vi.fn()
vi.mock('@/api/product', () => ({
  pageProducts: (...a: unknown[]) => pageProducts(...a),
  SORT_FIELD: { sales: 'salesCount', price: 'price' },
}))
const listCategories = vi.fn()
vi.mock('@/api/category', () => ({
  listCategories: (...a: unknown[]) => listCategories(...a),
  buildCategoryTree: (l: unknown[]) => l,
}))

const HomeView = (await import('./HomeView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
    { path: '/software', component: { template: '<div/>' } },
    { path: '/solutions', component: { template: '<div/>' } },
    { path: '/product/:id', component: { template: '<div/>' } },
    { path: '/templates', component: { template: '<div/>' } },
  ],
})

function spu(id: number, over: Partial<ProductSpu> = {}): ProductSpu {
  return {
    id,
    name: `真实商品 ${id}`,
    introduction: `副标题 ${id}`,
    categoryId: 1,
    picUrl: `http://x/${id}.png`,
    sliderPicUrls: [],
    specType: false,
    price: 1290 + id,
    marketPrice: 1990,
    stock: 10,
    salesCount: 100 - id,
    deliveryTypes: [1],
    ...over,
  }
}

async function mountHome(products: ProductSpu[] = [spu(1), spu(2), spu(3), spu(4)]) {
  pageProducts.mockResolvedValue({ list: products, total: products.length })
  listCategories.mockResolvedValue([{ id: 1, parentId: 0, name: '标签耗材', picUrl: '' }])
  const w = mount(HomeView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(() => {
  setActivePinia(createPinia())
  window.localStorage.clear()
})

describe('HomeView —— 商品区用真实商品填充', () => {
  it('按销量取前 4 个（FR-008）', async () => {
    await mountHome()
    expect(pageProducts).toHaveBeenCalledWith(
      expect.objectContaining({ sortField: 'salesCount', sortAsc: false, pageSize: 4 }),
    )
  })

  it('展示的是**后台真实商品**，不是设计稿写死的那 4 个', async () => {
    const w = await mountHome()
    expect(w.text()).toContain('真实商品 1')
    expect(w.text()).toContain('真实商品 4')
    // 设计稿写死的商品名一个都不应出现
    for (const hardcoded of ['赋签 M1 家用智能标签打印机', '赋签 M3 Pro 商用高速打印机', '赋签 M5 工业级宽幅打印机']) {
      expect(w.text()).not.toContain(hardcoded)
    }
  })

  it('**后台商品销量全为 0 时仍展示真实商品，商品区不空白**', async () => {
    const w = await mountHome([spu(1, { salesCount: 0 }), spu(2, { salesCount: 0 })])
    expect(w.text()).toContain('真实商品 1')
    expect(w.findAll('.p-card').length).toBe(2)
  })

  it('后台没有商品时展示空状态而不是空白区', async () => {
    const w = await mountHome([])
    expect(w.find('.empty-state').exists()).toBe(true)
  })

  it('商品卡片**不出现促销活动标签**（FR-026h）', async () => {
    const w = await mountHome()
    for (const t of ['满减', '满 10 卷', '限时折扣', '秒杀']) {
      expect(w.text()).not.toContain(t)
    }
  })
})

describe('HomeView —— 轮播用设计稿的结构', () => {
  it('Hero 是 3 张幻灯片（与设计稿一致），并生成 3 个圆点', async () => {
    const w = await mountHome()
    expect(w.findAll('.hero .car-slide')).toHaveLength(3)
    expect(w.findAll('.hero .car-dot')).toHaveLength(3)
  })
})

describe('HomeView —— 信任背书区的未确认声明走占位符（FR-056）', () => {
  it('数据条的数字带 data-content-pending', async () => {
    const w = await mountHome()
    const trust = w.find('.trust')
    expect(trust.exists()).toBe(true)
    expect(trust.findAll('[data-content-pending]').length).toBeGreaterThan(0)
  })

  it('客户 logo 行的公司名带 data-content-pending（未获授权不得展示）', async () => {
    const w = await mountHome()
    const row = w.find('.logo-row')
    expect(row.exists()).toBe(true)
    expect(row.findAll('[data-content-pending]').length).toBeGreaterThan(0)
  })

  it('客户证言与署名带 data-content-pending（匿名且不可核实）', async () => {
    const w = await mountHome()
    const quote = w.find('.quote')
    expect(quote.findAll('[data-content-pending]').length).toBeGreaterThan(0)
  })

  it('**不出现写死的「50,000+」等未经确认的数字**', async () => {
    const w = await mountHome()
    // 占位符里会带 [[ ]]，但绝不能是裸的已确认数字
    expect(w.text()).not.toMatch(/50,000\+\s*企业用户/)
  })
})

describe('HomeView —— 请求失败要有可重试提示（FR-045）', () => {
  it('商品加载失败时展示错误态与重试按钮，不白屏也不永久加载', async () => {
    pageProducts.mockRejectedValue(new Error('Network Error'))
    const w = mount(HomeView, { global: { plugins: [router, createPinia()] } })
    await flushPromises()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('加载失败')
    expect(w.get('.es-reset').text()).toBe('重新加载')
    // 不能还停在加载骨架
    expect(w.find('.ml-skeleton').exists()).toBe(false)
  })

  it('点「重新加载」会再次请求', async () => {
    pageProducts.mockRejectedValueOnce(new Error('boom'))
    const w = mount(HomeView, { global: { plugins: [router, createPinia()] } })
    await flushPromises()
    const calls = pageProducts.mock.calls.length
    pageProducts.mockResolvedValue({ list: [], total: 0 })
    await w.get('.es-reset').trigger('click')
    await flushPromises()
    expect(pageProducts.mock.calls.length).toBeGreaterThan(calls)
  })
})

describe('HomeView —— 交易相关的写死内容已清理（FR-043）', () => {
  it('不出现写死的「购物车 (2)」与假的商品总数', async () => {
    const w = await mountHome()
    expect(w.text()).not.toContain('购物车 (2)')
    expect(w.text()).not.toContain('共 48 件商品')
  })
})
