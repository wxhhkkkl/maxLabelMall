import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/mall/product/spu', () => ({
  getSpuSimpleList: vi.fn(async () => []),
  getSpu: vi.fn()
}))
vi.mock('@/api/mall/product/subscriptionPlan', () => ({
  getSubscriptionPlanPage: vi.fn(),
  updateSubscriptionPlan: vi.fn()
}))

import { getSubscriptionPlanPage } from '@/api/mall/product/subscriptionPlan'
import PlanIndex from './index.vue'

const getPage = vi.mocked(getSubscriptionPlanPage)

const directive = { mounted: () => {} }

beforeEach(() => {
  getPage.mockReset()
})

describe('会员授权商品页', () => {
  it('展示计划并在缺少月卡/年卡时提示', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [
        { id: 11, spuId: 300, skuId: 301, spuName: 'MaxLabel 会员授权', periodMonths: 1, enabled: true, sort: 1, price: 2900 }
      ]
    })
    const wrapper = mount(PlanIndex, { global: { plugins: [createPinia()], directives: { hasPermi: directive } } })
    await flushPromises()
    expect(wrapper.text()).toContain('MaxLabel 会员授权')
    expect(wrapper.text()).toContain('缺少 1 个月或 12 个月周期')
  })

  it('同一类别启用两个不同商品时给出提示（月卡/年卡共用同一 SPU 不算冲突）', async () => {
    getPage.mockResolvedValue({
      total: 2,
      list: [
        { id: 11, spuId: 300, skuId: 301, spuName: '会员授权 A', periodMonths: 1, scope: 1, enabled: true, sort: 1, price: 2900 },
        { id: 12, spuId: 400, skuId: 401, spuName: '会员授权 B', periodMonths: 12, scope: 1, enabled: true, sort: 2, price: 29900 }
      ]
    })
    const wrapper = mount(PlanIndex, { global: { plugins: [createPinia()], directives: { hasPermi: directive } } })
    await flushPromises()
    expect(wrapper.text()).toContain('同一类别同一时刻只能启用一条商品映射')
  })

  it('展示计划类别（企业 / 个人）', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [
        { id: 11, spuId: 300, skuId: 301, spuName: '企业授权', periodMonths: 12, scope: 2, enabled: true, sort: 1, price: 99000 }
      ]
    })
    const wrapper = mount(PlanIndex, { global: { plugins: [createPinia()], directives: { hasPermi: directive } } })
    await flushPromises()
    expect(wrapper.text()).toContain('企业')
    expect(wrapper.text()).toContain('企业授权')
  })

  it('展示桌面端版本档位', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [
        { id: 11, spuId: 300, skuId: 301, spuName: '企业授权', periodMonths: 12, scope: 2, edition: 'enterprise', enabled: true, sort: 1, price: 99000 }
      ]
    })
    const wrapper = mount(PlanIndex, { global: { plugins: [createPinia()], directives: { hasPermi: directive } } })
    await flushPromises()
    expect(wrapper.text()).toContain('桌面端版本')
    expect(wrapper.text()).toContain('企业版')
  })

  it('停用后不再提示周期缺失（仅剩停用计划时提示没有启用计划）', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [
        { id: 11, spuId: 300, skuId: 301, spuName: 'MaxLabel 会员授权', periodMonths: 12, enabled: false, sort: 1, price: 29900 }
      ]
    })
    const wrapper = mount(PlanIndex, { global: { plugins: [createPinia()], directives: { hasPermi: directive } } })
    await flushPromises()
    expect(wrapper.text()).toContain('当前没有启用的会员授权计划')
  })
})
