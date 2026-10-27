import { describe, expect, it, vi } from 'vitest'

vi.mock('@/config/http', () => ({ get: vi.fn() }))

import { get } from '@/config/http'
import { getMySubscription, listSubscriptionPlans } from './subscription'

const request = vi.mocked(get)

describe('会员授权 API', () => {
  it('读取匿名可访问的周期列表（含版本）', async () => {
    request.mockResolvedValue([
      { spuId: 300, skuId: 301, name: 'MaxLabel 会员授权', edition: 'professional', periodMonths: 1, price: 2900 }
    ])
    const plans = await listSubscriptionPlans()
    expect(request).toHaveBeenCalledWith('/product/subscription-plan/list')
    expect(plans[0]).toMatchObject({ skuId: 301, edition: 'professional', periodMonths: 1, price: 2900 })
  })

  it('读取当前会员的订阅状态与到期时间', async () => {
    request.mockResolvedValue({
      active: true,
      edition: 'professional',
      endDate: '2027-10-26',
      remainingDays: 365,
    })
    const subscription = await getMySubscription()
    expect(request).toHaveBeenCalledWith('/member/subscription/get')
    expect(subscription).toMatchObject({
      active: true,
      edition: 'professional',
      endDate: '2027-10-26',
      remainingDays: 365,
    })
  })
})
