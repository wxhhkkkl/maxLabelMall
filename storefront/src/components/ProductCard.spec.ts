import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'

const addToCart = vi.fn()
vi.mock('@/api/cart', () => ({
  addToCart: (...a: unknown[]) => addToCart(...a),
  getCartCount: vi.fn().mockResolvedValue(1),
  listCart: vi.fn(),
  updateCartCount: vi.fn(),
  updateCartSelected: vi.fn(),
  deleteCartItems: vi.fn(),
}))

const getProductDetail = vi.fn()
vi.mock('@/api/product', () => ({
  getProductDetail: (...a: unknown[]) => getProductDetail(...a),
}))

import type { ProductSpu } from '@/types'

import ProductCard from './ProductCard.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
    { path: '/product/:id', component: { template: '<div/>' } },
  ],
})

function sku(id: number) {
  return {
    id,
    properties: [{ propertyId: 1, propertyName: '版本', valueId: id, valueName: '标配' }],
    price: 1290,
    marketPrice: 0,
    stock: 9,
  }
}

function spu(over: Partial<ProductSpu> = {}): ProductSpu {
  return {
    id: 7,
    name: '三防热敏标签纸 40×30mm',
    introduction: '副标题不该出现在卡片上',
    categoryId: 10,
    picUrl: 'http://x/a.png',
    sliderPicUrls: [],
    specType: false,
    price: 1290,
    marketPrice: 1990,
    stock: 100,
    salesCount: 5,
    deliveryTypes: [1],
    ...over,
  }
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  addToCart.mockReset()
  addToCart.mockResolvedValue(1)
  getProductDetail.mockReset()
  await router.push('/mall')
  await router.isReady()
})

function mountCard(props: { spu: ProductSpu; badge?: 'hot' | 'fresh' | null }) {
  return mount(ProductCard, { props, global: { plugins: [router, createPinia()] } })
}

describe('ProductCard —— 用真实商品数据渲染', () => {
  it('展示名称、主图与售价（分为单位，展示为元）', () => {
    const w = mountCard({ spu: spu(), badge: null })
    expect(w.get('.p-title').text()).toContain('三防热敏标签纸 40×30mm')
    expect(w.get('.p-price').text()).toContain('¥12.90')
    // 主图：有 picUrl 用 img，没有则用设计稿的占位块 .ph
    expect(w.find('img').exists()).toBe(true)
  })

  it('有划线价时展示，且与售价是两个不同的元素', () => {
    const w = mountCard({ spu: spu(), badge: null })
    expect(w.text()).toContain('¥19.90')
    expect(w.get('.p-price').text()).not.toContain('¥19.90')
  })

  it('无划线价（marketPrice 为 0）时不展示划线价', () => {
    const w = mountCard({ spu: spu({ marketPrice: 0 }), badge: null })
    expect(w.text()).not.toContain('¥0.00')
  })

  it('主图为空时用设计稿的占位块，而不是一个损坏的 img', () => {
    const w = mountCard({ spu: spu({ picUrl: '' }), badge: null })
    expect(w.find('img').exists()).toBe(false)
    expect(w.find('.ph').exists()).toBe(true)
  })

  it('整卡可点进详情页（不是设计稿里的 .html）', () => {
    const w = mountCard({ spu: spu(), badge: null })
    expect(w.get('a.p-card').attributes('href')).toBe('/product/7')
  })
})

describe('角标 —— 由真实数据派生，不写死', () => {
  it('挂「热销」角标', () => {
    const w = mountCard({ spu: spu(), badge: 'hot' })
    expect(w.get('.p-tags .p-tag').text()).toBe('热销')
  })

  it('挂「新品」角标', () => {
    const w = mountCard({ spu: spu(), badge: 'fresh' })
    expect(w.get('.p-tags .p-tag').text()).toBe('新品')
  })

  it('**不出现「旗舰」与「订阅」** —— 后端无此字段，本期不展示', () => {
    for (const badge of ['hot', 'fresh', null] as const) {
      const t = mountCard({ spu: spu(), badge }).text()
      expect(t).not.toContain('旗舰')
      expect(t).not.toContain('订阅')
    }
  })

  it('无角标时**不渲染空的角标容器**', () => {
    const w = mountCard({ spu: spu(), badge: null })
    expect(w.find('.p-tags').exists()).toBe(false)
  })
})

describe('FR-026h —— 卡片上不得出现促销活动标签', () => {
  it('不渲染任何促销活动标签', () => {
    const w = mountCard({ spu: spu(), badge: 'hot' })
    const text = w.text()
    for (const t of ['满减', '满 10 卷', '限时折扣', '秒杀', '拼团', '首单立减']) {
      expect(text).not.toContain(t)
    }
  })
})

describe('加购入口（随 US3 接线）', () => {
  it('已登录时点加购会真正发请求', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    const w = mountCard({ spu: spu({ specType: false, skus: [sku(100)] }), badge: null })
    await w.get('.p-btn').trigger('click')
    await flushPromises()
    expect(addToCart).toHaveBeenCalledWith(100, 1)
  })

  it('**未登录时不发请求**，而是暂存意图并引导登录（FR-015）', async () => {
    const { useCartStore } = await import('@/store/cart')
    const w = mountCard({ spu: spu({ specType: false, skus: [sku(100)] }), badge: null })
    await w.get('.p-btn').trigger('click')
    await flushPromises()
    expect(addToCart).not.toHaveBeenCalled()
    // 意图已暂存，登录后会被补上
    expect(useCartStore().pendingIntent).toEqual({ skuId: 100, count: 1 })
  })

  it('**多规格商品在卡片上不能静默加购** —— 先去详情页选规格（FR-018）', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    const w = mountCard({
      spu: spu({ specType: true, skus: [sku(100), sku(101)] }),
      badge: null,
    })
    await w.get('.p-btn').trigger('click')
    await flushPromises()
    expect(addToCart).not.toHaveBeenCalled()
  })

  /**
   * 商城的**列表接口不返回 `skus`**（AppProductSpuRespVO 里没有该字段），
   * 但卡片的手上只有 SPU。早先这里直接取 `spus.skus?.[0]?.id ?? 0`，
   * 于是真实链路里发出去的是 `{"skuId":0}` —— 后端拒绝，用户看到的是
   * 「点了加购没反应」。上面几条用例之所以没发现，是因为夹具自己塞了 skus。
   */
  it('列表接口没给 skus 时，按需拉一次详情拿真实 SKU（**不能拿 0 去加购**）', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    getProductDetail.mockResolvedValue({ ...spu({ specType: false }), skus: [sku(100)] })

    const w = mountCard({ spu: spu({ specType: false }), badge: null })
    await w.get('.p-btn').trigger('click')
    await flushPromises()

    expect(getProductDetail).toHaveBeenCalledWith(7)
    expect(addToCart).toHaveBeenCalledWith(100, 1)
  })

  it('拉详情才知道是多规格 → 不静默加购，引导去详情页选规格（FR-018）', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    getProductDetail.mockResolvedValue({ ...spu({ specType: true }), skus: [sku(100), sku(101)] })

    const w = mountCard({ spu: spu({ specType: false }), badge: null })
    await w.get('.p-btn').trigger('click')
    await flushPromises()

    expect(addToCart).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/product/7')
  })
})
