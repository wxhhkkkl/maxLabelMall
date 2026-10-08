import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

/**
 * 视口宽度桩。
 *
 * jsdom **不实现** `matchMedia` 的媒体查询求值（`matches` 恒为 false），所以
 * "宽屏取 6 个"这条分支没法靠真实宽度测到，必须把 `matchMedia` 换成桩。
 * 组件在 `setup` 里就读它，所以**必须在 `mount` 之前**调用。
 */
function stubViewport(wide: boolean): void {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: wide,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }))
}

beforeEach(() => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  // 默认按窄屏。需要宽屏的用例自己在 mount 之前再调一次 stubViewport(true)。
  stubViewport(false)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('HomeView —— 商品区用真实商品填充', () => {
  /**
   * 取几个由**视口宽度**决定（2026-10-07 所有者决策）：超宽屏 6 个、其余 4 个。
   * 门槛 1601px 与 design.css 里 `.product-grid` 的 `@media (max-width: 1600px)`
   * **严格互补** —— 取 6 个却排 4 列的话第三张卡会孤零零占一行。
   * （断点值本身由 `src/styles/product-grid.spec.ts` 钉住，这里只测取数行为。）
   */
  it('未达门槛（≤1600px）按销量取前 4 个', async () => {
    stubViewport(false)
    await mountHome()
    expect(pageProducts).toHaveBeenCalledWith(
      expect.objectContaining({ sortField: 'salesCount', sortAsc: false, pageSize: 4 }),
    )
  })

  it('超宽屏（≥1601px）按销量取前 6 个，并渲染 6 张卡', async () => {
    stubViewport(true)
    const six = [spu(1), spu(2), spu(3), spu(4), spu(5), spu(6)]
    const w = await mountHome(six)
    expect(pageProducts).toHaveBeenCalledWith(
      expect.objectContaining({ sortField: 'salesCount', sortAsc: false, pageSize: 6 }),
    )
    expect(w.findAll('.p-card')).toHaveLength(6)
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

  it('第一张是讲「标签耗材」的那张（商城导流放在首屏第一眼）', async () => {
    const w = await mountHome()
    const first = w.findAll('.hero .car-slide')[0]!
    expect(first.text()).toContain('标签耗材设备')
    expect(first.text()).toContain('一站购齐更省心')
  })
})

describe('HomeView —— 首屏配图与小图标', () => {
  /**
   * 三张 Hero 配图由业务方 2026-10-08 提供，放在 `public/assets/`。
   * 顺序与幻灯片内容**一一对应**（商城耗材 / 打印机 / 软件界面），
   * 这里把文件名和顺序一起钉住 —— 换图或调顺序时这条会红。
   */
  it('三张幻灯片的配图是真实图片，且与各自的内容对应', async () => {
    const w = await mountHome()
    const visuals = w.findAll('.hero .hero-visual')
    expect(visuals).toHaveLength(3)
    expect(visuals.map((v) => v.attributes('src'))).toEqual([
      '/assets/hero-mall.png',
      '/assets/hero-printer.png',
      '/assets/hero-software.png',
    ])
    // 占位块（div）换成了 img，且每张都有非空 alt
    for (const v of visuals) {
      expect(v.element.tagName).toBe('IMG')
      expect(v.attributes('alt')?.trim()).toBeTruthy()
    }
  })

  /**
   * 软件功能卡与服务支持卡的图标。设计稿（已删除，可回溯 git `9df135c3`）里
   * `.f-card` 与 `.s-card` 各有内联 SVG；实现时漏掉了，页面上只剩标题与正文。
   * 不引入图标库（硬约束），一律内联 SVG，`currentColor` 也**不用** ——
   * 设计稿给的是固定色（功能卡 #6FC8FF、服务卡 #1B66FF），所以直接钉住 fill/stroke。
   */
  it('四张软件功能卡各带一个内联 SVG 图标', async () => {
    const w = await mountHome()
    const cards = w.findAll('.f-card')
    expect(cards).toHaveLength(4)
    for (const c of cards) expect(c.find('svg').exists()).toBe(true)
  })

  it('四张服务支持卡各带一个内联 SVG 图标', async () => {
    const w = await mountHome()
    const cards = w.findAll('.s-card')
    expect(cards).toHaveLength(4)
    for (const c of cards) expect(c.find('svg').exists()).toBe(true)
  })
})

describe('HomeView —— Hero 右侧小标签（设计稿三张都有）', () => {
  /**
   * 设计稿里三张幻灯片**都**带右侧玻璃小标签，实现时只做了第 2 张（打印机那张），
   * 另外两张漏了 —— 2026-10-08 补齐。顺序与幻灯片一致：耗材 / 打印机 / 软件。
   */
  it('三张幻灯片各带 2 个小标签', async () => {
    const w = await mountHome()
    const slides = w.findAll('.hero .car-slide')
    expect(slides).toHaveLength(3)
    for (const [i, s] of slides.entries()) {
      expect(s.findAll('.glass-chip'), `第 ${i + 1} 张幻灯片`).toHaveLength(2)
    }
  })

  /**
   * 设计稿的标签里有一个**报价**（¥12.9 /卷 起）和两个数字/效果承诺。
   * 按 FR-056 一律走占位符，业务方确认前不得当作事实展示。
   */
  it('报价 / 数字 / 效果承诺类标签文案带 data-content-pending', async () => {
    const w = await mountHome()
    const flagged = w.findAll('.hero .glass-chip [data-content-pending]').map((e) => e.text())
    expect(flagged).toContain('¥12.9 /卷 起')
    expect(flagged).toContain('2,000+ 持续更新')
    expect(flagged).toContain('效率提升 10 倍')
  })

  /**
   * 门禁③ 的视图层：标签文案必须全部来自 `placeholders.ts`，
   * 视图里不得再出现任何一句（第 2 张那两句原本是硬编码的）。
   *
   * ⚠️ 这里**只查标签专属的字符串**，没有把 HomeView 整个纳入门禁③ ——
   * 这个视图里本来就散布着大量设计稿文案字面量（幻灯片标题/副标题、功能卡、行业卡…），
   * 那是更大的一笔账，不在本次改动范围内。像「Excel 批量打印」这种**同时**出现在
   * 第 3 张副标题和功能卡里的，就用它自己的断言去管，不算标签泄漏。
   */
  it('标签文案没有硬编码在视图里（门禁③）', () => {
    // ⚠️ 不能用 `import.meta.url` + fileURLToPath —— jsdom 环境下它不是 file: 协议，
    //    会抛 "The URL must be of scheme file"。照 design-consistency.spec.ts 的做法用 cwd。
    const src = readFileSync(join(process.cwd(), 'src', 'views', 'HomeView.vue'), 'utf8')
    for (const t of ['赋签 M3 Pro', 'MaxLabel 云标签', '双模高速 · 300dpi', '多端同步 · 批量打印',
      '三防热敏纸', '蜡基碳带', '110mm×300m', '海量模板库', '¥12.9', '2,000+ 持续更新', '效率提升 10 倍']) {
      expect(src, `视图里不该出现「${t}」`).not.toContain(t)
    }
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

  it('**未经确认的数字仍被标记为待确认**（方括号不再显示，改由属性保证）', async () => {
    const w = await mountHome()
    const stat = w.findAll('.stat')[0]!
    expect(stat.text()).toContain('50,000+')
    // ⚠️ 这条断言原先写作 `not.toMatch(/50,000\+\s*企业用户/)`，它**靠方括号才通过**：
    // 渲染成 `[[50,000+]][[企业用户]]` 时中间隔着 `]] [[`，正则匹配不上。方括号不再显示后
    // textContent 变成 `50,000+企业用户`，而 `\s*` 允许零空格，那条断言会**误报失败**。
    // 「未经确认」这个事实现在只能靠属性表达 —— 所以要断言属性在，而不是去匹配文本。
    expect(stat.findAll('[data-content-pending]')).toHaveLength(2) // 数字、标签各一处
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
