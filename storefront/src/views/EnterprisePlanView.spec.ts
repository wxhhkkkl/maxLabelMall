import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/api/enterprisePlan', () => ({ getEnterprisePlan: vi.fn() }))

import { getEnterprisePlan } from '@/api/enterprisePlan'
import EnterprisePlanView from './EnterprisePlanView.vue'

const loadPlan = vi.mocked(getEnterprisePlan)

beforeEach(() => {
  loadPlan.mockReset()
  push.mockReset()
})

describe('企业商品详情', () => {
  it('展示价格与合计，并带数量进入结算', async () => {
    loadPlan.mockResolvedValue({
      spuId: 501,
      skuId: 601,
      name: 'MaxLabel 企业授权',
      periodMonths: 12,
      price: 99000,
      stock: 99,
    })
    const view = mount(EnterprisePlanView, { props: { skuId: '601' } })
    await flushPromises()

    expect(view.text()).toContain('MaxLabel 企业授权')
    expect(view.text()).toContain('1 年')
    expect(view.text()).toContain('¥990.00')
    expect(view.text()).toContain('企业授权订单必须使用企业类型发票抬头')

    await view.find('button.ent-plan-buy').trigger('click')
    expect(push).toHaveBeenCalledWith({
      path: '/checkout',
      query: { source: 'product', skuId: '601', count: '1' },
    })
  })

  it('加载失败有明确提示', async () => {
    loadPlan.mockRejectedValue(new Error('网络错误'))
    const view = mount(EnterprisePlanView, { props: { skuId: '601' } })
    await flushPromises()
    expect(view.text()).toContain('网络错误')
  })
})
