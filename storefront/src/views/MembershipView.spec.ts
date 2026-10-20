import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/api/subscription', () => ({ listSubscriptionPlans: vi.fn() }))

import { listSubscriptionPlans } from '@/api/subscription'
import MembershipView from './MembershipView.vue'

const listPlans = vi.mocked(listSubscriptionPlans)

beforeEach(() => {
  listPlans.mockReset()
  push.mockReset()
})

describe('会员授权页', () => {
  it('渲染月/年周期并在点击后进入商品详情', async () => {
    listPlans.mockResolvedValue([
      { spuId: 300, skuId: 301, name: 'MaxLabel 会员授权', periodMonths: 1, price: 2900, stock: 99 },
      { spuId: 300, skuId: 302, name: 'MaxLabel 会员授权', periodMonths: 12, price: 29900, stock: 99 }
    ])
    const view = mount(MembershipView)
    await flushPromises()

    expect(view.findAll('.membership-card')).toHaveLength(2)
    expect(view.text()).toContain('1 个月')
    expect(view.text()).toContain('1 年')
    expect(view.text()).toContain('¥29.00')
    expect(view.text()).toContain('¥299.00')

    await view.findAll('.membership-card button')[0]!.trigger('click')
    expect(push).toHaveBeenCalledWith('/product/300')
  })

  it('空计划与加载失败都有明确提示', async () => {
    listPlans.mockResolvedValue([])
    const empty = mount(MembershipView)
    await flushPromises()
    expect(empty.text()).toContain('暂无可购买的会员授权')

    listPlans.mockRejectedValue(new Error('网络错误'))
    const failed = mount(MembershipView)
    await flushPromises()
    expect(failed.text()).toContain('网络错误')
  })
})
