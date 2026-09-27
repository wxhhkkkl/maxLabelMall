import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { RENDERED } from '@/data/placeholders'

const AboutView = (await import('./AboutView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/about', component: { template: '<div/>' } },
    { path: '/contact', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
  ],
})

async function mountView() {
  const w = mount(AboutView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  await router.push('/about')
  await router.isReady()
})

describe('AboutView —— 版式（FR-059 / §3.14）', () => {
  it('页首含标题与一句话定位', async () => {
    const w = await mountView()
    expect(w.get('.ab-title').text()).toContain('关于赋签')
    expect(w.get('.ml-page-sub').text()).toContain('用一枚标签')
  })

  it('图文段逐段渲染，插图用 `.ph` 占位块', async () => {
    const w = await mountView()
    expect(w.findAll('.ab-row')).toHaveLength(RENDERED.about.intro.length)
    expect(w.findAll('.ab-row .ph')).toHaveLength(RENDERED.about.intro.length)
  })

  it('图文段**左右交替**（左文右图 / 左图右文）', async () => {
    const w = await mountView()
    const rows = w.findAll('.ab-row')
    expect(rows[0]?.classes()).not.toContain('is-reverse')
    expect(rows[1]?.classes()).toContain('is-reverse')
  })

  it('数据条逐项渲染 label 与 value', async () => {
    const w = await mountView()
    const stats = w.findAll('.ab-stat')
    expect(stats).toHaveLength(RENDERED.about.stats.length)
    expect(stats[0]?.text()).toContain('成立年份')
  })

  it('发展历程按时间线渲染，含年份与事件', async () => {
    const w = await mountView()
    const ms = w.findAll('.ab-milestone')
    expect(ms).toHaveLength(RENDERED.about.milestones.length)
    expect(ms[0]?.text()).toBeTruthy()
  })

  it('底部给出 CTA', async () => {
    const w = await mountView()
    expect(w.find('.cta-btn').exists()).toBe(true)
  })
})

describe('AboutView —— 不得编造公司事实（FR-054 / FR-059）', () => {
  it('所有公司事实都带 `data-content-pending`', async () => {
    const w = await mountView()
    // 3 段简介 + 4 项数据(标签+数值) + 5 条历程(年份+事件) —— 至少这么多处
    expect(w.findAll('[data-content-pending]').length).toBeGreaterThanOrEqual(3 + 8 + 10)
  })

  it('**不出现任何具体年份** —— 业务方未提供前不许写成像真的一样', async () => {
    const w = await mountView()
    // 种子里用的是 `20XX`，不是 2015 / 2023 这类可被当成事实的年份
    expect(w.text()).not.toMatch(/\b(19|20)\d{2}\b/)
  })

  it('不出现设计稿里那些未经确认的数字声明', async () => {
    const w = await mountView()
    expect(w.text()).not.toMatch(/50,?000\+|2,?000\+|99\.9%/)
  })
})
