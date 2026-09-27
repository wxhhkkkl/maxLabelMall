import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Category, ProductSpu } from '@/types'

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

const MallView = (await import('./MallView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/mall', component: { template: '<div/>' } },
    { path: '/product/:id', component: { template: '<div/>' } },
  ],
})

function spu(id: number, over: Partial<ProductSpu> = {}): ProductSpu {
  return {
    id,
    name: `真实商品 ${id}`,
    introduction: '副标题',
    categoryId: 10,
    picUrl: 'http://x/a.png',
    sliderPicUrls: [],
    specType: false,
    price: 1290,
    marketPrice: 0,
    stock: 5,
    salesCount: 1,
    deliveryTypes: [1],
    ...over,
  }
}

const CATS: Category[] = [
  { id: 10, parentId: 0, name: '标签耗材', picUrl: '' },
  { id: 11, parentId: 10, name: '三防热敏纸', picUrl: '' },
  { id: 20, parentId: 0, name: '打印设备', picUrl: '' },
]

async function mountMall(list = [spu(1), spu(2)], total = 2) {
  pageProducts.mockResolvedValue({ list, total })
  listCategories.mockResolvedValue(CATS)
  const w = mount(MallView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

/** 取最后一次请求参数 */
function lastParams() {
  return pageProducts.mock.calls.at(-1)?.[0] as Record<string, unknown>
}

beforeEach(() => {
  setActivePinia(createPinia())
  window.localStorage.clear()
})

describe('MallView —— 商品网格与总数', () => {
  it('展示真实商品', async () => {
    const w = await mountMall()
    expect(w.text()).toContain('真实商品 1')
    expect(w.findAll('.p-card')).toHaveLength(2)
  })

  it('列表头部「共 N 件商品」取自**分页结果的 total**（不是写死的）', async () => {
    const w = await mountMall([spu(1)], 48)
    expect(w.get('.sort-count').text()).toContain('48')
  })

  it('首页取 12 条一页（后端默认分页参数）', async () => {
    await mountMall()
    expect(lastParams().pageSize).toBe(12)
  })
})

describe('MallView —— 搜索走后端全量检索（FR-003）', () => {
  it('关键词作为 keyword 参数发给后端，**不是前端过滤当前页**', async () => {
    const w = await mountMall()
    await w.get('#mallSearch').setValue('碳带')
    await w.get('#mallSearchBtn').trigger('click')
    await flushPromises()
    expect(lastParams().keyword).toBe('碳带')
  })

  it('搜索后回到第 1 页（否则会停在一个空的高页码上）', async () => {
    const w = await mountMall()
    // 先翻到第 2 页
    pageProducts.mockResolvedValue({ list: [spu(9)], total: 40 })
    await flushPromises()
    await w.get('#mallSearch').setValue('热敏')
    await w.get('#mallSearchBtn').trigger('click')
    await flushPromises()
    expect(lastParams().pageNo).toBe(1)
  })

  it('搜索无结果时展示空状态，而不是空白网格', async () => {
    const w = await mountMall([], 0)
    await w.get('#mallSearch').setValue('不存在的商品')
    await w.get('#mallSearchBtn').trigger('click')
    await flushPromises()
    pageProducts.mockResolvedValue({ list: [], total: 0 })
    expect(w.find('.empty-state').exists()).toBe(true)
  })
})

describe('MallView —— 排序（FR-004）', () => {
  it('默认综合排序：**不传 sortField**', async () => {
    await mountMall()
    expect(lastParams().sortField).toBeUndefined()
  })

  it('销量优先 → salesCount 降序', async () => {
    const w = await mountMall()
    await w.findAll('.sort-chip')[1]?.trigger('click')
    await flushPromises()
    expect(lastParams().sortField).toBe('salesCount')
    expect(lastParams().sortAsc).toBe(false)
  })

  it('价格从低到高 → price 升序', async () => {
    const w = await mountMall()
    await w.findAll('.sort-chip')[2]?.trigger('click')
    await flushPromises()
    expect(lastParams().sortField).toBe('price')
    expect(lastParams().sortAsc).toBe(true)
  })
})

describe('MallView —— 分类筛选（FR-002）', () => {
  it('点分类胶囊按 categoryId 筛选', async () => {
    const w = await mountMall()
    const pills = w.findAll('.cat-pills .tab')
    // 第 1 个是「全部」，第 2 个是第一个真实分类
    await pills[1]?.trigger('click')
    await flushPromises()
    expect(lastParams().categoryId).toBe(10)
  })

  it('点「全部」清掉分类筛选', async () => {
    const w = await mountMall()
    await w.findAll('.cat-pills .tab')[1]?.trigger('click')
    await flushPromises()
    await w.findAll('.cat-pills .tab')[0]?.trigger('click')
    await flushPromises()
    expect(lastParams().categoryId).toBeUndefined()
  })

  it('侧栏渲染多级分类树', async () => {
    const w = await mountMall()
    expect(w.findAll('.cat-group').length).toBeGreaterThan(0)
    expect(w.text()).toContain('三防热敏纸')
  })

  it('**「企业采购」标签指向落地页**（FR-048）—— 它不是一个商品分类', async () => {
    const w = await mountMall()
    const pill = w.findAll('.cat-pills .tab').find((t) => t.text() === '企业采购')
    expect(pill).toBeTruthy()
    // 必须是指向 /enterprise 的链接，而不是一个会去按 categoryId 筛选的分类
    expect(pill?.attributes('href')).toBe('/enterprise')
    expect(lastParams().categoryId).toBeUndefined()
  })
})

describe('MallView —— 移除后端不支持的 UI（FR-004a / FR-002a）', () => {
  it('**没有「价格区间」与「服务」两组筛选项**', async () => {
    const w = await mountMall()
    const text = w.text()
    expect(text).not.toContain('价格区间')
    expect(text).not.toContain('¥0 - ¥50')
    expect(text).not.toContain('¥50 - ¥300')
    expect(text).not.toContain('¥1000 以上')
    expect(text).not.toContain('正品保障')
    expect(text).not.toContain('当日发货')
    expect(text).not.toContain('企业专享价')
  })

  it('**侧栏没有各类目的商品数量计数**（后端不返回该信息）', async () => {
    const w = await mountMall()
    // 「全部商品 48」这类计数不应存在
    expect(w.find('.cat-all .cnt').exists()).toBe(false)
    expect(w.findAll('.cat-sub .cnt')).toHaveLength(0)
  })

  it('不出现设计稿写死的「共 48 件商品」', async () => {
    const w = await mountMall()
    expect(w.text()).not.toContain('共 48 件商品')
  })
})

describe('MallView —— 请求失败要有可重试提示（FR-045）', () => {
  it('商品加载失败时展示错误态与重试按钮', async () => {
    pageProducts.mockRejectedValue(new Error('Network Error'))
    listCategories.mockResolvedValue(CATS)
    const w = mount(MallView, { global: { plugins: [router, createPinia()] } })
    await flushPromises()
    expect(w.text()).toContain('加载失败')
    expect(w.get('.es-reset').text()).toBe('重新加载')
    expect(w.find('.ml-skeleton').exists()).toBe(false)
  })

  it('分类接口失败也不抛未处理的拒绝', async () => {
    pageProducts.mockResolvedValue({ list: [], total: 0 })
    listCategories.mockRejectedValue(new Error('boom'))
    const w = mount(MallView, { global: { plugins: [router, createPinia()] } })
    await flushPromises()
    // 页面仍然渲染出来（不崩）
    expect(w.find('.mall-page-main').exists()).toBe(true)
  })
})

describe('MallView —— 分页', () => {
  it('总数超过一页时渲染分页', async () => {
    const w = await mountMall([spu(1)], 40)
    expect(w.find('.pager').exists()).toBe(true)
  })

  it('翻页把 pageNo 传给后端', async () => {
    const w = await mountMall([spu(1)], 40)
    await w.findAll('.pager .pg')[1]?.trigger('click')
    await flushPromises()
    expect(lastParams().pageNo).toBe(2)
  })

  it('只有一页时不渲染分页', async () => {
    const w = await mountMall([spu(1)], 1)
    expect(w.find('.pager').exists()).toBe(false)
  })
})
