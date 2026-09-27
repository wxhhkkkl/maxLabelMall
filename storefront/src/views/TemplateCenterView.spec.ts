import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { RENDERED, isPending } from '@/data/placeholders'

const TemplateCenterView = (await import('./TemplateCenterView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/templates', component: { template: '<div/>' } },
    { path: '/software', component: { template: '<div/>' } },
  ],
})

/** 种子里的行业名（**不含**「全部」，它是全选而不是一个行业） */
const INDUSTRIES = (RENDERED.templates.categories as string[]).slice(1)

async function mountView() {
  const w = mount(TemplateCenterView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

/** 搜索框输入并触发（客户端匹配，FR-057） */
async function search(w: Awaited<ReturnType<typeof mountView>>, kw: string) {
  await w.get('#tplSearch').setValue(kw)
  await w.get('#tplSearchBtn').trigger('click')
  await flushPromises()
}

beforeEach(async () => {
  setActivePinia(createPinia())
  await router.push('/templates')
  await router.isReady()
})

describe('TemplateCenterView —— 版式与内容（FR-057 / §3.12）', () => {
  it('渲染页首（标题 + 副标题）与搜索框', async () => {
    const w = await mountView()
    // §3.12 的页首是 `.sol-hero` 风格，不是 `.ml-page-head`
    expect(w.get('.sol-hero h1').text()).toContain('模板')
    expect(w.get('.sol-hero p').text()).toContain('模板')
    expect(w.find('#tplSearch').exists()).toBe(true)
  })

  it('**行业胶囊取自 `placeholders.ts` 的分类数组**，且以「全部」默认选中', async () => {
    const w = await mountView()
    const strip = (s: string) => s.replace(/^\[\[|\]\]$/g, '')
    const labels = w.findAll('.cat-pills .tab').map((t) => strip(t.text()))
    for (const industry of INDUSTRIES) {
      expect(labels).toContain(strip(industry))
    }
    expect(strip(w.get('.cat-pills .tab.active').text())).toBe(strip(RENDERED.templates.categories[0]))
  })

  it('**胶囊文案保留占位标记** —— 行业名也是待业务方确认的内容，不能显示得像已确认', async () => {
    const w = await mountView()
    expect(w.findAll('.cat-pills .tab [data-content-pending]').length).toBe(
      RENDERED.templates.categories.length,
    )
  })

  it('渲染模板卡网格，含名称、行业与尺寸占位', async () => {
    const w = await mountView()
    expect(w.findAll('.tpl-card').length).toBeGreaterThan(0)
    expect(w.get('.tpl-card').find('.tpl-thumb').exists()).toBe(true)
  })

  it('**总数用业务方待确认的占位文案，不沿用设计稿的「2,000+」**（FR-055）', async () => {
    const w = await mountView()
    expect(w.text()).not.toMatch(/2,?000\+/)
    // 数量表述必须是待确认的占位（会带 data-content-pending），不能是写死的数字
    expect(isPending(RENDERED.templates.countText)).toBe(true)
  })
})

describe('TemplateCenterView —— 搜索（FR-057：对模板名与行业做客户端匹配）', () => {
  it('按**模板名**匹配', async () => {
    const w = await mountView()
    await search(w, '价签')
    expect(w.findAll('.tpl-card')).toHaveLength(1)
  })

  it('按**行业**也能匹配到（不只是名称）', async () => {
    const w = await mountView()
    await search(w, '物流')
    // 种子里有「物流面单 · 标准」「箱唛 · 大号」两张物流模板
    expect(w.findAll('.tpl-card')).toHaveLength(2)
  })

  it('无结果时给**空状态**，不是空白网格', async () => {
    const w = await mountView()
    await search(w, '不存在的关键词xyz')
    expect(w.findAll('.tpl-card')).toHaveLength(0)
    expect(w.find('.empty-state').exists()).toBe(true)
  })
})

describe('TemplateCenterView —— 行业胶囊过滤（FR-057）', () => {
  it('点行业胶囊只留下该行业，点「全部」恢复', async () => {
    const w = await mountView()
    const strip = (s: string) => s.replace(/^\[\[|\]\]$/g, '')
    const tab = w.findAll('.cat-pills .tab').find((t) => strip(t.text()) === '物流')
    expect(tab).toBeTruthy()
    await tab?.trigger('click')
    await flushPromises()
    expect(w.findAll('.tpl-card')).toHaveLength(2)

    await w.findAll('.cat-pills .tab')[0]?.trigger('click')
    await flushPromises()
    expect(w.findAll('.tpl-card').length).toBeGreaterThan(2)
  })
})

describe('TemplateCenterView —— 分页（FR-057）', () => {
  it('条目多于每页数量时出现分页，切页后数量变化', async () => {
    const w = await mountView()
    const total = RENDERED.templates.items.length
    const first = w.findAll('.tpl-card').length
    expect(first).toBeLessThan(total) // 说明确实分页了
    expect(w.find('.pager').exists()).toBe(true)

    await w.get('.pg-next').trigger('click')
    await flushPromises()
    expect(w.findAll('.tpl-card')).toHaveLength(total - first)
  })
})
