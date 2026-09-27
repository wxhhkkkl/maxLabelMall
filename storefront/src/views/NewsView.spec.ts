import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

type Article = { category: string; title: string; summary: string; date: string }

/** 种子用可变引用注入，便于直接测「筛选为空 / 列表为空」两条空态（FR-060） */
const seed = vi.hoisted(() => ({ items: [] as Article[] }))

vi.mock('@/data/placeholders', async (orig) => {
  const real = await orig<typeof import('@/data/placeholders')>()
  return { ...real, RENDERED: { ...real.RENDERED, news: seed.items } }
})

const NewsView = (await import('./NewsView.vue')).default
const { newsCategories } = await import('@/data/placeholders')

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/news', component: { template: '<div/>' } },
  ],
})

function article(category: string, n: number): Article {
  return {
    category,
    title: `[[标题 ${n}]]`,
    summary: `[[摘要 ${n}]]`,
    date: `[[20XX-XX-0${n}]]`,
  }
}

/** 6 条：公司新闻 3 / 行业资讯 2 / 产品动态 1 —— 每页 4 条，故有分页 */
function fill(): void {
  seed.items.splice(
    0,
    seed.items.length,
    article('公司新闻', 1),
    article('公司新闻', 2),
    article('公司新闻', 3),
    article('行业资讯', 4),
    article('行业资讯', 5),
    article('产品动态', 6),
  )
}

async function mountView() {
  const w = mount(NewsView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  fill()
  await router.push('/news')
  await router.isReady()
})

describe('NewsView —— 胶囊与列表（FR-060 / §3.15）', () => {
  it('胶囊是「全部」+ 种子里的分类数组，默认选中「全部」', async () => {
    const w = await mountView()
    const labels = w.findAll('.cat-pills .tab').map((t) => t.text())
    expect(labels[0]).toBe('全部')
    for (const c of newsCategories) expect(labels).toContain(c)
    expect(w.get('.cat-pills .tab.active').text()).toBe('全部')
  })

  it('新闻卡含缩略图占位、标题、摘要与日期', async () => {
    const w = await mountView()
    const card = w.get('.news-card')
    expect(card.find('.ph').exists()).toBe(true)
    expect(card.find('.news-title').text()).toContain('标题')
    expect(card.find('.news-summary').text()).toContain('摘要')
    expect(card.find('.news-date').text()).toContain('20XX')
  })

  it('摘要做了**两行截断**（不是把全文铺开）', async () => {
    const w = await mountView()
    expect(w.get('.news-summary').classes()).toContain('is-clamp-2')
  })

  it('标题与摘要经 `PendingText` 渲染（业务方未提供前带占位标记）', async () => {
    const w = await mountView()
    expect(w.findAll('[data-content-pending]').length).toBeGreaterThanOrEqual(4)
  })
})

describe('NewsView —— 分类过滤（FR-060：点击即过滤）', () => {
  it('点分类胶囊只留该分类', async () => {
    const w = await mountView()
    const tab = w.findAll('.cat-pills .tab').find((t) => t.text() === '公司新闻')
    await tab?.trigger('click')
    await flushPromises()
    expect(w.findAll('.news-card')).toHaveLength(3)
  })

  it('点回「全部」恢复', async () => {
    const w = await mountView()
    await w.findAll('.cat-pills .tab').find((t) => t.text() === '产品动态')?.trigger('click')
    await flushPromises()
    await w.findAll('.cat-pills .tab')[0]?.trigger('click')
    await flushPromises()
    expect(w.findAll('.news-card').length).toBeGreaterThan(1)
  })

  it('**筛选结果为空时给空状态**', async () => {
    // 只留「公司新闻」的文章，再去点「产品动态」
    seed.items.splice(2)
    const w = await mountView()
    await w.findAll('.cat-pills .tab').find((t) => t.text() === '产品动态')?.trigger('click')
    await flushPromises()
    expect(w.findAll('.news-card')).toHaveLength(0)
    expect(w.find('.empty-state').exists()).toBe(true)
  })
})

describe('NewsView —— 空态与分页（FR-060）', () => {
  it('**列表为空时给空状态**，不是空白页', async () => {
    seed.items.splice(0, seed.items.length)
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.findAll('.news-card')).toHaveLength(0)
  })

  it('条目多于每页数量时出现分页', async () => {
    const w = await mountView()
    expect(w.find('.pager').exists()).toBe(true)
    const first = w.findAll('.news-card').length
    await w.get('.pg-next').trigger('click')
    await flushPromises()
    expect(w.findAll('.news-card')).toHaveLength(seed.items.length - first)
  })
})
