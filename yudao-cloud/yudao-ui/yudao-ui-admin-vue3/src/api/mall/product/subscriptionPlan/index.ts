import request from '@/config/axios'

export interface SubscriptionPlanVO {
  id: number
  spuId: number
  skuId: number
  spuName?: string
  skuName?: string
  price?: number
  periodMonths: number
  /** 类别：1 个人 2 企业 */
  scope: number
  enabled: boolean
  sort: number
  createTime?: string
}

export interface SubscriptionPlanSaveVO {
  id?: number
  spuId: number
  skuId: number
  periodMonths: number
  /** 类别：1 个人 2 企业 */
  scope?: number
  enabled: boolean
  sort?: number
}

export const getSubscriptionPlanPage = async (params: {
  pageNo: number
  pageSize: number
  spuId?: number
  scope?: number
  enabled?: boolean
}) => {
  return await request.get<{ list: SubscriptionPlanVO[]; total: number }>({
    url: '/product/subscription-plan/page',
    params
  })
}

export const getSubscriptionPlan = async (id: number) => {
  return await request.get<SubscriptionPlanVO>({
    url: '/product/subscription-plan/get',
    params: { id }
  })
}

export const createSubscriptionPlan = async (data: SubscriptionPlanSaveVO) => {
  return await request.post({ url: '/product/subscription-plan/create', data })
}

export const updateSubscriptionPlan = async (data: SubscriptionPlanSaveVO) => {
  return await request.put({ url: '/product/subscription-plan/update', data })
}
