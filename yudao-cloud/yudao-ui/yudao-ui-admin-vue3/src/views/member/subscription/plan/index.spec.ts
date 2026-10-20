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
