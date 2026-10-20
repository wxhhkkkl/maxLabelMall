import { get } from '@/config/http'

/** MaxLabel 会员授权周期（商品公开信息，可匿名读取）。 */
export interface SubscriptionPlan {
  spuId: number
  skuId: number
  name: string
  periodMonths: number
  price: number
  picUrl?: string
  stock?: number
  sort?: number
}

export function listSubscriptionPlans(): Promise<SubscriptionPlan[]> {
  return get<SubscriptionPlan[]>('/product/subscription-plan/list')
}
