import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

type Item = { kind: string; text: string }
type Entry = { version: string; date: string; items: Item[] }

/**
 * 种子用**可变引用**注入，这样能直接测「内容为空」那条分支（FR-058 要求空态），
 * 而不必为了造空数据去改真实种子。
 */
const seed = vi.hoisted(() => ({ entries: [] as Entry[] }))

vi.mock('@/data/placeholders', async (orig) => {
  const real = await orig<typeof import('@/data/placeholders')>()
  return { ...real, RENDERED: { ...real.RENDERED, changelog: seed.entries } }
})

const ChangelogView = (await import('./ChangelogView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/changelog', component: { template: '<div/>' } },
  ],
})

/** 三条版本，类型标签覆盖 新增/优化/修复 三类 */
function fill(): void {
  seed.entries.splice(0, seed.entries.length, {
    version: '[[v3.0.X]]',
    date: '[[20XX-XX-XX]]',
    items: [
      { kind: '新增', text: '[[云端模板库支持多端实时同步]]' },
      { kind: '优化', text: '[[大幅缩短大批量任务的生成时间]]' },
      { kind: '修复', text: '[[修正部分型号打印机边距偏移的问题]]' },
    ],
  })
}

async function mountView() {
  const w = mount(ChangelogView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  fill()
  await router.push('/changelog')
  await router.isReady()
})

describe('ChangelogView —— 时间线（FR-058 / §3.13）', () => {
  it('每条含版本号与日期', async () => {
    const w = await mountView()
    expect(w.findAll('.cl-entry')).toHaveLength(1)
    expect(w.get('.cl-version').text()).toContain('v3.0.X')
    expect(w.get('.cl-date').text()).toContain('20XX-XX-XX')
  })

  it('更新项逐条列出，各带类型标签', async () => {
    const w = await mountView()
    expect(w.findAll('.cl-item')).toHaveLength(3)
    const kinds = w.findAll('.cl-kind').map((k) => k.text())
    expect(kinds).toEqual(['新增', '优化', '修复'])
  })

  it('**类型标签只有 新增/优化/修复 三类**，且视觉上可区分', async () => {
    const w = await mountView()
    const classes = w.findAll('.cl-kind').map((k) => k.classes().join(' '))
    expect(classes.every((c) => /is-(added|improved|fixed)/.test(c))).toBe(true)
    // 三类底色两两不同 —— 光靠文案区分不算"标签"
    const distinct = new Set(classes)
    expect(distinct.size).toBe(3)
  })

  it('版本与更新项文案经 `PendingText` 渲染（业务方未确认前带占位标记）', async () => {
    const w = await mountView()
    expect(w.findAll('[data-content-pending]').length).toBeGreaterThanOrEqual(5)
    expect(w.text()).toContain('云端模板库支持多端实时同步')
  })

  it('多条版本按顺序渲染', async () => {
    seed.entries.push({
      version: '[[v2.9.X]]',
      date: '[[20XX-XX-XX]]',
      items: [{ kind: '优化', text: '[[模板编辑器的操作响应速度]]' }],
    })
    const w = await mountView()
    expect(w.findAll('.cl-entry')).toHaveLength(2)
    // 注意：占位文案会**带着 `[[ ]]` 一起渲染**（这正是它可辨识的原因），
    // 所以这里用包含而不是全等
    const versions = w.findAll('.cl-version').map((v) => v.text())
    expect(versions[0]).toContain('v3.0.X')
    expect(versions[1]).toContain('v2.9.X')
  })
})

describe('ChangelogView —— 空态（FR-058：内容为空时必须给空状态）', () => {
  it('**没有版本条目时显示空状态**，不是空白页面', async () => {
    seed.entries.splice(0, seed.entries.length)
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.findAll('.cl-entry')).toHaveLength(0)
  })
})
