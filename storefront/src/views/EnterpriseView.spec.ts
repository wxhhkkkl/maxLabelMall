import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { RENDERED } from '@/data/placeholders'

const EnterpriseView = (await import('./EnterpriseView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/enterprise', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
    { path: '/contact', component: { template: '<div/>' } },
  ],
})

async function mountView() {
  const w = mount(EnterpriseView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  await router.push('/enterprise')
  await router.isReady()
})

describe('EnterpriseView —— 权益（FR-047）', () => {
  it('展示三项企业采购权益，文案取自占位种子（不得内联）', async () => {
    const w = await mountView()
    for (const b of RENDERED.enterprise.benefits) {
      expect(w.text()).toContain(b.name.replace(/^\[\[|\]\]$/g, ''))
    }
    expect(RENDERED.enterprise.benefits).toHaveLength(3)
  })

  it('**企业事实类文案带 `data-content-pending`** —— 业务方未确认前不得当作真话发布', async () => {
    const w = await mountView()
    expect(w.findAll('[data-content-pending]').length).toBeGreaterThan(0)
  })

  it('**不出现设计稿里那句未经确认的承诺**（FR-056：企业客户支持月结属待确认营销承诺）', async () => {
    const w = await mountView()
    // 页面可以介绍「月结账期」这项权益，但不能把设计稿的承诺语句原样搬来当事实
    expect(w.text()).not.toContain('企业客户支持月结')
  })
})

describe('EnterpriseView —— 联系方式（FR-047 / FR-061）', () => {
  it('给出至少一种联系途径', async () => {
    const w = await mountView()
    expect(w.find('.ent-hotline').exists()).toBe(true)
    expect(w.find('.ent-wechat').exists()).toBe(true)
  })

  it('**占位期间不给假的可拨号链接** —— 否则等于在提供打不通的号码（FR-054）', async () => {
    const w = await mountView()
    const anchors = w.findAll('a').map((a) => a.attributes('href') ?? '')
    expect(anchors.some((h) => h.startsWith('tel:'))).toBe(false)
  })
})

describe('EnterpriseView —— 不得产生线上企业交易能力（FR-049）', () => {
  /**
   * ⚠️ 断言的是**入口与控件**，不是页面文案里出现哪些词。
   * 「月结」作为**权益说明**是允许的（种子文案写的是「通过资质审核的企业客户可申请
   * 月结，账期与结算方式按协议约定执行」，即线下按协议办理）；FR-049 禁的是**在页面上
   * 或其链路上**提供线上下单 / 授信 / 月结下单的**能力**。
   */
  const FORBIDDEN_ENTRY = /(立即下单|在线下单|申请授信|开通账期|月结下单|在线授信)/

  it('**不存在**导向线上企业订单 / 授信 / 月结下单的入口', async () => {
    const w = await mountView()
    for (const el of w.findAll('a, button')) {
      expect(el.text()).not.toMatch(FORBIDDEN_ENTRY)
    }
    // 链接里也不许出现交易类落点
    for (const a of w.findAll('a')) {
      const href = a.attributes('href') ?? ''
      expect(href).not.toMatch(/checkout|order|credit|settle|pay/)
    }
  })

  it('没有任何提交表单（企业交易在线上商城之外完成）', async () => {
    const w = await mountView()
    expect(w.find('form').exists()).toBe(false)
    expect(w.find('input').exists()).toBe(false)
  })
})
