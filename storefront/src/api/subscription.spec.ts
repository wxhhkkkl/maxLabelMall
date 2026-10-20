import { describe, expect, it, vi } from 'vitest'

vi.mock('@/config/http', () => ({ get: vi.fn() }))

import { get } from '@/config/http'
import { listSubscriptionPlans } from './subscription'

const request = vi.mocked(get)

describe('会员授权 API', () => {
  it('读取匿名可访问的周期列表', async () => {
    request.mockResolvedValue([
      { spuId: 300, skuId: 301, name: 'MaxLabel 会员授权', periodMonths: 1, price: 2900 }
    ])
    const plans = await listSubscriptionPlans()
    expect(request).toHaveBeenCalledWith('/product/subscription-plan/list')
    expect(plans[0]).toMatchObject({ skuId: 301, periodMonths: 1, price: 2900 })
  })
})
