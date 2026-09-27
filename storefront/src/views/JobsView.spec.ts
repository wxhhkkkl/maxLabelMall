import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

type Job = {
  category: string
  title: string
  city: string
  experience: string
  responsibilities: string[]
  requirements: string[]
  applyTo: string
}

/** 种子用可变引用注入，便于测「筛选为空」空态（FR-062） */
const seed = vi.hoisted(() => ({ items: [] as Job[] }))

vi.mock('@/data/placeholders', async (orig) => {
  const real = await orig<typeof import('@/data/placeholders')>()
  return { ...real, RENDERED: { ...real.RENDERED, jobs: seed.items } }
})

const JobsView = (await import('./JobsView.vue')).default
const { jobCategories } = await import('@/data/placeholders')

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/jobs', component: { template: '<div/>' } },
  ],
})

function job(category: string, n: number): Job {
  return {
    category,
    title: `[[岗位 ${n}]]`,
    city: `[[城市 ${n}]]`,
    experience: '[[3 年以上]]',
    responsibilities: [`[[职责 A${n}]]`, `[[职责 B${n}]]`],
    requirements: [`[[要求 A${n}]]`],
    applyTo: `[[投递邮箱待填写 ${n}]]`,
  }
}

function fill(): void {
  seed.items.splice(
    0,
    seed.items.length,
    job('技术', 1),
    job('技术', 2),
    job('产品', 3),
  )
}

async function mountView() {
  const w = mount(JobsView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  fill()
  await router.push('/jobs')
  await router.isReady()
})

describe('JobsView —— 胶囊与岗位卡（FR-062 / §3.17）', () => {
  it('胶囊是「全部」+ 种子里的职能分类，默认选中「全部」', async () => {
    const w = await mountView()
    const labels = w.findAll('.cat-pills .tab').map((t) => t.text())
    expect(labels[0]).toBe('全部')
    for (const c of jobCategories) expect(labels).toContain(c)
    expect(w.get('.cat-pills .tab.active').text()).toBe('全部')
  })

  it('岗位卡展示职位名、城市与经验要求', async () => {
    const w = await mountView()
    const card = w.get('.job-card')
    expect(card.find('.job-title').text()).toContain('岗位')
    expect(card.find('.job-meta').text()).toContain('城市')
    expect(card.find('.job-meta').text()).toContain('3 年以上')
  })

  it('岗位事实经 `PendingText` 渲染（业务方未提供前带占位标记）', async () => {
    const w = await mountView()
    expect(w.findAll('[data-content-pending]').length).toBeGreaterThanOrEqual(3)
  })
})

describe('JobsView —— 展开详情（职责 / 要求 / 投递方式）', () => {
  it('默认不展开，点「查看详情」后展开', async () => {
    const w = await mountView()
    expect(w.find('.job-detail').exists()).toBe(false)

    await w.get('.job-toggle').trigger('click')
    await flushPromises()
    expect(w.get('.job-detail').text()).toContain('岗位职责')
    expect(w.get('.job-detail').text()).toContain('任职要求')
    expect(w.get('.job-detail').text()).toContain('投递方式')
  })

  it('再点一次收起', async () => {
    const w = await mountView()
    await w.get('.job-toggle').trigger('click')
    await flushPromises()
    await w.get('.job-toggle').trigger('click')
    await flushPromises()
    expect(w.find('.job-detail').exists()).toBe(false)
  })

  it('展开的是**那一个**岗位，不是所有岗位一起展开', async () => {
    const w = await mountView()
    await w.findAll('.job-toggle')[1]?.trigger('click')
    await flushPromises()
    expect(w.findAll('.job-detail')).toHaveLength(1)
    expect(w.findAll('.job-card')[1]?.find('.job-detail').exists()).toBe(true)
  })
})

describe('JobsView —— 分类过滤与空态（FR-062）', () => {
  it('点职能胶囊只留该职能', async () => {
    const w = await mountView()
    await w.findAll('.cat-pills .tab').find((t) => t.text() === '技术')?.trigger('click')
    await flushPromises()
    expect(w.findAll('.job-card')).toHaveLength(2)
  })

  it('**筛选为空时给空状态**', async () => {
    // 只留「技术」岗，再去点「产品」
    seed.items.splice(2)
    const w = await mountView()
    await w.findAll('.cat-pills .tab').find((t) => t.text() === '产品')?.trigger('click')
    await flushPromises()
    expect(w.findAll('.job-card')).toHaveLength(0)
    expect(w.find('.empty-state').exists()).toBe(true)
  })

  it('**列表为空时给空状态**', async () => {
    seed.items.splice(0, seed.items.length)
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
  })
})
