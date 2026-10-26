import { get } from '@/config/http'

/** 企业授权商品（仅企业 OWNER/ADMIN 可读，需登录）。 */
export interface EnterprisePlan {
  spuId: number
  skuId: number
  name: string
  periodMonths: number
  price: number
  picUrl?: string
  stock?: number
  sort?: number
  description?: string
}

export function listEnterprisePlans(): Promise<EnterprisePlan[]> {
  return get<EnterprisePlan[]>('/product/subscription-plan/enterprise/list')
}

export function getEnterprisePlan(skuId: number): Promise<EnterprisePlan> {
  return get<EnterprisePlan>('/product/subscription-plan/enterprise/get', { skuId })
}
