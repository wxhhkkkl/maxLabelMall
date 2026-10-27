import { get } from '@/config/http'

/** MaxLabel 会员授权周期（商品公开信息，可匿名读取）。 */
export interface SubscriptionPlan {
  spuId: number
  skuId: number
  name: string
  /** 桌面端版本：standard 标准版 / professional 专业版 / enterprise 企业版 */
  edition?: string
  periodMonths: number
  price: number
  picUrl?: string
  stock?: number
  sort?: number
}

/** 当前会员的订阅状态（需登录）。 */
export interface MySubscription {
  memberId?: number
  active?: boolean
  startDate?: string
  endDate?: string
  startTime?: string
  endTime?: string
  remainingDays?: number
  periodMonths?: number
  sourceOrderNo?: string
  /** 当前生效的最高版本；无有效订阅时为空。 */
  edition?: string | null
  activeEditions?: string[]
  /** 权益来源：1 个人订阅 / 2 企业席位 */
  sourceType?: number
  orgId?: number
  orgName?: string
}

export function listSubscriptionPlans(): Promise<SubscriptionPlan[]> {
  return get<SubscriptionPlan[]>('/product/subscription-plan/list')
}

export function getMySubscription(): Promise<MySubscription> {
  return get<MySubscription>('/member/subscription/get')
}
