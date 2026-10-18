import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
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

// 顶部横幅（T238）。默认空数组 —— 绝大多数用例不关心它
const listBanners = vi.fn()
vi.mock('@/api/banner', () => ({
  BANNER_POSITION: { HOME: 1, MALL: 6 },
  listBanners: (...a: unknown[]) => listBanners(...a),
}))

// 筛选维度（特性 003 / US15）
const listFilterDimensions = vi.fn()
vi.mock('@/api/filter', () => ({
  listFilterDimensions: (...a: unknown[]) => listFilterDimensions(...a),
}))

const MallView = (await import('./MallView.vue')).default
enableAutoUnmount(afterEach)

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
  // 列表用例从商城首页进入全部商品；品类分区由 MallDepartments.spec.ts 覆盖。
  await w.get('.cat-all').trigger('click')
  await flushPromises()
  return w
}

/** 取最后一次请求参数 */
function lastParams() {
  return pageProducts.mock.calls.at(-1)?.[0] as Record<string, unknown>
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  // 横幅默认空 —— 只有专门测它的用例才覆盖
  listBanners.mockReset()
  listBanners.mockResolvedValue([])
  // 默认"这个分类没配任何维度" —— 只有专门测筛选的用例才覆盖它
  listFilterDimensions.mockReset()
  listFilterDimensions.mockResolvedValue([])
  await router.push('/mall')
})

describe('MallView —— 商品网格与总数', () => {
  it('首页加载分类和楼层商品时始终不插入筛选列表布局', async () => {
    let resolveCategories!: (value: Category[]) => void
    let resolveProducts!: (value: { list: ProductSpu[]; total: number }) => void
    listCategories.mockImplementationOnce(() => new Promise(resolve => { resolveCategories = resolve }))
    pageProducts.mockImplementation(() => new Promise(resolve => { resolveProducts = resolve }))
    const w = mount(MallView, { global: { plugins: [router, createPinia()] } })
    expect(w.find('.mall-home-loading').exists()).toBe(true)
    expect(w.find('.mall-page-main').exists()).toBe(false)
    resolveCategories(CATS)
    await flushPromises()
    expect(w.find('.mall-home').exists()).toBe(true)
    expect(w.find('.mall-page-main').exists()).toBe(false)
    pageProducts.mockResolvedValue({ list: [], total: 0 })
    resolveProducts({ list: [], total: 0 })
    await flushPromises()
    expect(w.find('.mall-home').exists()).toBe(true)
  })

  it('分类直达链接从首帧就进入筛选布局，不先显示首页', async () => {
    await router.push('/mall?category=10')
    listCategories.mockResolvedValue(CATS)
    pageProducts.mockResolvedValue({ list: [], total: 0 })
    const w = mount(MallView, { global: { plugins: [router, createPinia()] } })
    expect(w.find('.mall-home').exists()).toBe(false)
    expect(w.find('.mall-home-loading').exists()).toBe(false)
    expect(w.find('.mall-page-main').exists()).toBe(true)
    await flushPromises()
  })

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
    await w.get('#mallSearchBox').trigger('submit')
    await flushPromises()
    expect(lastParams().keyword).toBe('碳带')
  })

  it('搜索后回到第 1 页（否则会停在一个空的高页码上）', async () => {
    const w = await mountMall()
    // 先翻到第 2 页
    pageProducts.mockResolvedValue({ list: [spu(9)], total: 40 })
    await flushPromises()
    await w.get('#mallSearch').setValue('热敏')
    await w.get('#mallSearchBox').trigger('submit')
    await flushPromises()
    expect(lastParams().pageNo).toBe(1)
  })

  it('搜索无结果时展示空状态，而不是空白网格', async () => {
    const w = await mountMall([], 0)
    await w.get('#mallSearch').setValue('不存在的商品')
    await w.get('#mallSearchBox').trigger('submit')
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
  it('商城首页左侧目录：悬停一级分类展开二级分类，点二级分类按它查询', async () => {
    // `buildCategoryTree` 在本文件里是恒等映射，所以直接给带 children 的一级分类
    const nested = [{ ...CATS[0]!, children: [{ ...CATS[1]!, children: [] }] }, { ...CATS[2]!, children: [] }]
    pageProducts.mockResolvedValue({ list: [spu(1)], total: 1 })
    listCategories.mockResolvedValue(nested)
    const w = mount(MallView, { global: { plugins: [router, createPinia()] } })
    await flushPromises()
    // 类名以 `origin/main` 上那版实现为准（`.mall-directory-item` / `.mall-category-flyout`）——
    // 首页悬停分类这条线在主干上另有更完整的一版（延迟收起 / Esc / aria-controls），
    // 本文件不再断言它的内部结构，只钉**接线**：浮层里点二级分类，列表要按它查询
    await w.findAll('.mall-directory-item')[0]!.trigger('mouseenter')
    await w.get('.mall-category-flyout-links button').trigger('click')
    await flushPromises()
    expect(lastParams().categoryId).toBe(11)
    expect(listFilterDimensions).toHaveBeenLastCalledWith(11)
    expect(router.currentRoute.value.query.category).toBe('11')
  })

  it('一级分类显示二级选项，二级才加载维度，返回一级清除维度选择', async () => {
    const w = await mountMall()
    listFilterDimensions.mockResolvedValue(DIMENSIONS)
    await w.findAll('.cat-pills .tab')[1]!.trigger('click')
    await flushPromises()
    expect(w.get('[aria-label="二级分类"]').text()).toContain('三防热敏纸')
    expect(listFilterDimensions).not.toHaveBeenCalled()
    expect(w.find('.product-filter-bar').exists()).toBe(false)
    await w.get('.mall-child-options button').trigger('click')
    await flushPromises()
    expect(listFilterDimensions).toHaveBeenLastCalledWith(11)
    expect(w.find('.product-filter-bar').exists()).toBe(true)
    expect(w.findAll('.cat-pills .tab')[1]!.attributes('aria-pressed')).toBe('true')
    expect(w.get('.mall-child-options button').attributes('aria-pressed')).toBe('true')
    await w.get('.filter-value').trigger('click')
    await flushPromises()
    await w.findAll('.cat-pills .tab')[1]!.trigger('click')
    await flushPromises()
    expect(w.find('.product-filter-bar').exists()).toBe(false)
    expect(lastParams()).toMatchObject({ categoryId: 10, filterValueIds: [] })
  })

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

  it('企业采购咨询直接指向联系人页面', async () => {
    const w = await mountMall()
    const link = w.get('.mall-browse-heading a')
    expect(link.text()).toContain('企业采购咨询')
    expect(link.attributes('href')).toBe('/support/contact')
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
    await router.push('/mall?view=all')
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

describe('MallView —— 选购交互', () => {
  it('价格从高到低走后端降序', async () => {
    const w = await mountMall()
    await w.findAll('.sort-chip')[3]?.trigger('click')
    await flushPromises()
    expect(lastParams()).toMatchObject({ sortField: 'price', sortAsc: false })
  })

  it('未提交的搜索内容不改变分类查询，提交后仍可翻页', async () => {
    const w = await mountMall([spu(1)], 40)
    await w.get('#mallSearch').setValue('标签')
    await w.findAll('.cat-pills .tab')[1]?.trigger('click')
    await flushPromises()
    expect(lastParams().keyword).toBeUndefined()
    await w.get('#mallSearchBox').trigger('submit')
    await flushPromises()
    await w.findAll('.pager .pg')[1]?.trigger('click')
    await flushPromises()
    expect(lastParams()).toMatchObject({ keyword: '标签', categoryId: 10, pageNo: 2 })
    expect(w.find('.mall-active-filters').exists()).toBe(false)
    expect(w.text()).not.toContain('重置筛选')
    await w.get('#mallSearchClear').trigger('click')
    await w.get('.cat-all').trigger('click')
    await flushPromises()
    expect(lastParams()).toMatchObject({ pageNo: 1, keyword: undefined, categoryId: undefined })
  })

  it('视图切换不重新请求商品', async () => {
    const w = await mountMall()
    const calls = pageProducts.mock.calls.length
    await w.get('[aria-label="列表视图"]').trigger('click')
    expect(w.get('.mall-grid').classes()).toContain('is-list')
    expect(pageProducts.mock.calls.length).toBe(calls)
    await w.get('[aria-label="网格视图"]').trigger('click')
    expect(w.get('.mall-grid').classes()).not.toContain('is-list')
  })

  it('快速切换分类时，较早返回的请求不会覆盖当前结果', async () => {
    const w = await mountMall()
    let resolveOld!: (value: { list: ProductSpu[]; total: number }) => void
    pageProducts.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    await w.findAll('.cat-pills .tab')[1]?.trigger('click')
    pageProducts.mockResolvedValueOnce({ list: [spu(99)], total: 1 })
    await w.findAll('.cat-pills .tab')[2]?.trigger('click')
    await flushPromises()
    resolveOld({ list: [spu(88)], total: 1 })
    await flushPromises()
    expect(w.text()).toContain('真实商品 99')
    expect(w.text()).not.toContain('真实商品 88')
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

/**
 * 商城页顶部横幅（FR-080 / FR-082 / T238）。
 *
 * 页面这一层只需要保证两件事：**按「商城页」位置去拉**、以及**拉不到时不留空白**。
 * 三态的渲染细节由 `MallBanner.spec.ts` 覆盖。
 */
describe('MallView —— 顶部横幅', () => {
  it('按「商城页」位置（6）拉横幅', async () => {
    await mountMall()
    expect(listBanners).toHaveBeenCalledWith(6)
  })

  it('有横幅时渲染出来', async () => {
    listBanners.mockResolvedValue([{ id: 1, title: '活动', picUrl: 'http://x/a.png', url: 'http://x' }])
    const w = await mountMall()
    expect(w.find('.mall-banner-wrap').exists()).toBe(true)
  })

  it('**没有横幅时整块不渲染**（不留空白占位，商品列表照常）', async () => {
    const w = await mountMall()
    expect(w.find('.mall-banner-wrap').exists()).toBe(false)
    expect(w.findAll('.p-card').length).toBeGreaterThan(0)
  })

  it('**横幅接口挂了不影响商品列表**（辅助区块不该带崩这一页）', async () => {
    listBanners.mockRejectedValue(new Error('boom'))
    const w = await mountMall()
    expect(w.find('.mall-banner-wrap').exists()).toBe(false)
    // 商品照常渲染 —— 这才是关键：不能因为横幅失败就整页报错
    expect(w.findAll('.p-card').length).toBeGreaterThan(0)
    expect(w.text()).not.toContain('加载失败')
  })
})


// ============================================================================
// 筛选条（特性 003 / US15 / T291）
// ============================================================================

const DIMENSIONS = [
  { dimensionId: 12, dimensionName: '宽度', sort: 1, values: [
    { valueId: 3, name: '110', sort: 1 },
    { valueId: 4, name: '100', sort: 2 },
  ] },
  { dimensionId: 13, dimensionName: '长度', sort: 2, values: [
    { valueId: 5, name: '300m', sort: 1 },
  ] },
]

async function pickLabelChild(w: Awaited<ReturnType<typeof mountMall>>) {
  await w.findAll('.cat-pills .tab')[1]!.trigger('click')
  await flushPromises()
  await w.get('.mall-child-options button').trigger('click')
  await flushPromises()
}
/** 挂载后先切到某个分类（筛选条只在选中分类时才出现） */
async function mountMallWithFilter(dimensions: unknown = DIMENSIONS, list = [spu(1)], total = 1) {
  const w = await mountMall(list, total)
  listFilterDimensions.mockResolvedValue(dimensions)
  await pickLabelChild(w) // 「全部」之后的第一个分类
  await flushPromises()
  return w
}

describe('MallView —— 筛选条（FR-098 ~ FR-106）', () => {
  it('较早分类的维度后返回，不覆盖当前分类的筛选项', async () => {
    const w = await mountMall()
    let resolveOld!: (value: unknown) => void
    listFilterDimensions.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    await pickLabelChild(w)
    await flushPromises()
    expect(w.text()).toContain('正在加载分类筛选')
    listFilterDimensions.mockResolvedValueOnce([{ dimensionId: 99, dimensionName: '设备类型', sort: 1, values: [{ valueId: 99, name: '桌面型', sort: 1 }] }])
    await w.findAll('.cat-pills .tab')[2]!.trigger('click')
    await flushPromises()
    resolveOld(DIMENSIONS)
    await flushPromises()
    expect(w.get('.product-filter-bar').text()).toContain('设备类型')
    expect(w.get('.product-filter-bar').text()).not.toContain('宽度')
    expect(lastParams().categoryId).toBe(20)
  })

  it('维度加载期间改变排序，等待新分类条件确定后再查询', async () => {
    const w = await mountMallWithFilter()
    await w.get('.filter-value').trigger('click')
    await flushPromises()
    let resolveNext!: (value: unknown) => void
    listFilterDimensions.mockImplementationOnce(() => new Promise(resolve => { resolveNext = resolve }))
    await w.findAll('.cat-pills .tab')[2]!.trigger('click')
    await flushPromises()
    const calls = pageProducts.mock.calls.length
    await w.findAll('.sort-chip')[3]!.trigger('click')
    await flushPromises()
    expect(pageProducts.mock.calls.length).toBe(calls)
    resolveNext([])
    await flushPromises()
    expect(lastParams()).toMatchObject({ categoryId: 20, filterValueIds: [], sortField: 'price', sortAsc: false })
  })

  it('分类请求未完成时进入全部商品，旧条件不会重新出现', async () => {
    const w = await mountMallWithFilter()
    let resolveOld!: (value: unknown) => void
    listFilterDimensions.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    await w.findAll('.cat-pills .tab')[2]!.trigger('click')
    await flushPromises()
    await w.get('.cat-all').trigger('click')
    await flushPromises()
    resolveOld(DIMENSIONS)
    await flushPromises()
    expect(w.find('.product-filter-bar').exists()).toBe(false)
    expect(lastParams()).toMatchObject({ categoryId: undefined, filterValueIds: [] })
  })

  it('打开登录弹层不清除搜索、分类和维度条件', async () => {
    const w = await mountMallWithFilter()
    await w.get('.filter-value').trigger('click')
    await w.get('#mallSearch').setValue('碳带')
    await w.get('#mallSearchBox').trigger('submit')
    await flushPromises()
    const calls = pageProducts.mock.calls.length
    await router.replace({ query: { ...router.currentRoute.value.query, login: '1' } })
    await flushPromises()
    expect(w.get('#mallSearch').element).toHaveProperty('value', '碳带')
    expect(w.get('.filter-value.active').text()).toBe('110')
    expect(pageProducts.mock.calls.length).toBe(calls)
  })

  it('维度接口失败后可重试，仍能浏览商品', async () => {
    const w = await mountMall()
    listFilterDimensions.mockRejectedValueOnce(new Error('offline'))
    await pickLabelChild(w)
    await flushPromises()
    expect(w.find('.p-card').exists()).toBe(true)
    listFilterDimensions.mockResolvedValueOnce(DIMENSIONS)
    await w.get('.mall-filter-status button').trigger('click')
    await flushPromises()
    expect(w.find('.product-filter-bar').exists()).toBe(true)
    expect(w.find('.mall-filter-status').exists()).toBe(false)
  })

  it('选中分类后按该分类拉维度，并渲染出来', async () => {
    const w = await mountMallWithFilter()

    expect(listFilterDimensions).toHaveBeenCalled()
    expect(w.find('.product-filter-bar').exists()).toBe(true)
    expect(w.text()).toContain('宽度')
  })

  it('该分类没配维度时，筛选条**整块不渲染**（不留空白块，FR-104）', async () => {
    const w = await mountMallWithFilter([])

    expect(w.find('.product-filter-bar').exists()).toBe(false)
  })

  it('「全部商品」（没选分类）时不渲染筛选条', async () => {
    const w = await mountMall([spu(1)], 1)
    listFilterDimensions.mockResolvedValue(DIMENSIONS)

    expect(w.find('.product-filter-bar').exists()).toBe(false)
  })

  it('**维度列表接口挂了不影响商品列表**（筛选条降级，FR-106）', async () => {
    const w = await mountMall([spu(1)], 1)
    listFilterDimensions.mockRejectedValue(new Error('boom'))
    await pickLabelChild(w)
    await flushPromises()

    expect(w.find('.product-filter-bar').exists()).toBe(false)
    // 商品照常显示
    expect(w.text()).toContain('真实商品 1')
  })

  it('点一个值 → 请求体里的 filterValueIds **精确匹配**（不是模糊、不是空）', async () => {
    const w = await mountMallWithFilter()

    await w.findAll('.filter-row')[0].findAll('.filter-value')[0].trigger('click')
    await flushPromises()

    // ⚠️ 这一层 pageProducts 是 mock，拿到的是**数组**（序列化成 '3' 由 api/product.spec.ts 负责）
    expect(lastParams().filterValueIds).toEqual([3])
  })

  it('再点同行的另一个值 → **替换**（单选，Q9=B），请求体只带新的那个', async () => {
    const w = await mountMallWithFilter()

    await w.findAll('.filter-row')[0].findAll('.filter-value')[1].trigger('click')
    await flushPromises()

    expect(lastParams().filterValueIds).toEqual([4])
  })

  it('跨维度叠加：两个维度各选一个，请求体里两个都在', async () => {
    const w = await mountMallWithFilter()

    await w.findAll('.filter-row')[0].findAll('.filter-value')[0].trigger('click')
    await flushPromises()
    await w.findAll('.filter-row')[1].findAll('.filter-value')[0].trigger('click')
    await flushPromises()

    expect(lastParams().filterValueIds).toEqual([3, 5])
  })

  it('点「全部」清掉该维度，且不影响另一个维度', async () => {
    const w = await mountMallWithFilter()

    await w.findAll('.filter-row')[0].findAll('.filter-value')[0].trigger('click')
    await flushPromises()
    await w.findAll('.filter-row')[1].findAll('.filter-value')[0].trigger('click')
    await flushPromises()
    await w.findAll('.filter-row')[0].find('.filter-row__all').trigger('click')
    await flushPromises()

    expect(lastParams().filterValueIds).toEqual([5])
  })

  it('筛选条件变化时回到第 1 页（FR-102）', async () => {
    const w = await mountMallWithFilter(DIMENSIONS, [spu(1)], 100)

    await w.findAll('.pager .pg')[1].trigger('click') // 翻到第 2 页
    await flushPromises()
    expect(lastParams().pageNo).toBe(2)

    await w.findAll('.filter-row')[0].findAll('.filter-value')[0].trigger('click')
    await flushPromises()
    expect(lastParams().pageNo).toBe(1)
  })

  it('**请求失败时高亮退回原值**，不留下"选中的是新值、列表是旧的"（FR-115 / Q14）', async () => {
    const w = await mountMallWithFilter()

    pageProducts.mockRejectedValueOnce(new Error('boom'))
    await w.findAll('.filter-row')[0].findAll('.filter-value')[1].trigger('click')
    await flushPromises()

    // 没有任何值处于选中态 —— 高亮回到了点击前
    expect(w.findAll('.filter-value.active')).toHaveLength(0)
    expect(w.find('.filter-row__all.active').exists()).toBe(true)
  })

  it('**快速连点时以最后一次为准**：先发出的请求后返回也不能覆盖（FR-115）', async () => {
    const w = await mountMallWithFilter()

    // 第一次点击的请求悬而不决
    let resolveFirst: (v: unknown) => void = () => {}
    pageProducts.mockImplementationOnce(
      () => new Promise((resolve) => { resolveFirst = resolve })
    )
    await w.findAll('.filter-row')[0].findAll('.filter-value')[0].trigger('click') // 选 110

    // 还没回来就点了另一个
    await w.findAll('.filter-row')[0].findAll('.filter-value')[1].trigger('click') // 改选 100
    await flushPromises()

    // 现在第一个请求才姗姗来迟，且带着"110 的结果"
    resolveFirst({ list: [spu(99)], total: 1 })
    await flushPromises()

    // 界面必须停在"100"上，既不能显示 110 的结果，也不能让高亮跳回 110
    expect(w.findAll('.filter-value.active').map((el) => el.text())).toEqual(['100'])
    expect(w.text()).not.toContain('真实商品 99')
  })

  it('切换分类时，仍适用的选择保留；不再适用的重置（FR-103 / Q10=B）', async () => {
    const w = await mountMallWithFilter()
    await w.findAll('.filter-row')[0].findAll('.filter-value')[0].trigger('click')
    await flushPromises()
    expect(lastParams().filterValueIds).toEqual([3])

    // 切到另一个分类，但它继承的维度清单一样 → 选择该保留
    // （.tab 的下标：0=全部、1=标签耗材、2=打印设备，再往后是「企业采购」链接）
    await w.findAll('.cat-pills .tab')[2].trigger('click')
    await flushPromises()
    expect(lastParams().filterValueIds).toEqual([3])

    // 切回一个维度**完全不同**的分类 → 原选择必须被重置
    listFilterDimensions.mockResolvedValue([
      { dimensionId: 99, dimensionName: '类型', sort: 1, values: [{ valueId: 9, name: '桌面型', sort: 1 }] },
    ])
    await pickLabelChild(w)
    await flushPromises()
    expect(lastParams().filterValueIds).toEqual([])
  })
})
