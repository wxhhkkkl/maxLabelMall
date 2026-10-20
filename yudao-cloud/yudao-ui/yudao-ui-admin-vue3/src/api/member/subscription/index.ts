import request from '@/config/axios'

export interface MemberSubscriptionVO {
  id: number
  memberId: number
  periodMonths: number
  startDate: string
  endDate: string
  startTime: string
  endTime: string
  active: boolean
  remainingDays: number
  sourceOrderId: number
  sourceOrderNo: string
  refunded: boolean
  createTime: string
}

export const getSubscriptionPage = async (params: {
  userId: number
  pageNo: number
  pageSize: number
}) => {
  return await request.get<{ list: MemberSubscriptionVO[]; total: number }>({
    url: '/member/subscription/page',
    params
  })
}
