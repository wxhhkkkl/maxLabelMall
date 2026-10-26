import { describe, expect, it, vi } from 'vitest'

vi.mock('@/config/http', () => ({ get: vi.fn() }))

import { get } from '@/config/http'
import { getEnterprisePlan, listEnterprisePlans } from './enterprisePlan'

const request = vi.mocked(get)

describe('企业授权商品 API', () => {
  it('读取企业商品列表与详情', async () => {
    request.mockResolvedValueOnce([{ spuId: 501, skuId: 601, price: 99000 }])
    const plans = await listEnterprisePlans()
    expect(request).toHaveBeenCalledWith('/product/subscription-plan/enterprise/list')
    expect(plans[0]?.skuId).toBe(601)

    request.mockResolvedValueOnce({ spuId: 501, skuId: 601, name: '企业版' })
    const detail = await getEnterprisePlan(601)
    expect(request).toHaveBeenLastCalledWith('/product/subscription-plan/enterprise/get', { skuId: 601 })
    expect(detail.name).toBe('企业版')
  })
})
