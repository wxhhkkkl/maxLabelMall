import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { RENDERED } from '@/data/placeholders'

const ContactView = (await import('./ContactView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/contact', component: { template: '<div/>' } },
  ],
})

async function mountView() {
  const w = mount(ContactView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  await router.push('/contact')
  await router.isReady()
})

describe('ContactView —— 联系方式（FR-061 / §3.16）', () => {
  it('渲染页首标题与副标题', async () => {
    const w = await mountView()
    expect(w.get('.ml-page-title').text()).toContain('联系我们')
    expect(w.get('.ml-page-sub').text()).toContain('售前咨询')
  })

  it('四张联系卡（客服热线 / 企业合作 / 售后支持 / 媒体联络），各含图标位与名称', async () => {
    const w = await mountView()
    const cards = w.findAll('.contact-card')
    expect(cards).toHaveLength(RENDERED.contact.cards.length)
    expect(cards).toHaveLength(4)
    for (const c of cards) expect(c.find('.ph').exists()).toBe(true)
  })

  it('地址卡含地图占位与公司地址', async () => {
    const w = await mountView()
    expect(w.find('.map-ph').exists()).toBe(true)
    expect(w.get('.ml-addr').text()).toContain('地址')
  })

  it('给出服务时间说明', async () => {
    const w = await mountView()
    expect(w.text()).toContain('服务时间')
  })

  it('联系信息经 `PendingText` 渲染（业务方未提供前带占位标记）', async () => {
    const w = await mountView()
    expect(w.findAll('[data-content-pending]').length).toBeGreaterThanOrEqual(5)
  })
})

describe('ContactView —— 号码与占位（FR-054 / FR-061）', () => {
  it('**占位期间不给假的可拨号链接** —— 打不通的号码等于在骗人', async () => {
    const w = await mountView()
    const hrefs = w.findAll('a').map((a) => a.attributes('href') ?? '')
    expect(hrefs.some((h) => h.startsWith('tel:'))).toBe(false)
  })

  it('**不出现设计稿那个占位号码 `400-800-XXXX`**（FR-054：MUST NOT 作为正式号码发布）', async () => {
    const w = await mountView()
    expect(w.text()).not.toContain('400-800-XXXX')
    expect(w.text()).not.toMatch(/400-800-\d/)
  })

  it('不出现设计稿页脚那个占位号码形态', async () => {
    const w = await mountView()
    // 设计稿页脚写的是 `400-800-XXXX`，页面渲染的是「电话待填写」这类可辨识占位
    expect(w.text()).not.toMatch(/400-800/)
    expect(RENDERED.siteMeta.hotline).toContain('[[')
  })
})
