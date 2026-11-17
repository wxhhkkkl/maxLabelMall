import request from '@/config/axios'

/**
 * 客户服务（021）
 *
 * · `trade:customer-service:query` 看列表与详情
 * · `trade:customer-service:handle` 处理 / 代建 / 开单 / 改状态
 */
export interface CustomerServiceTicketVO {
  id: number
  memberId: number
  memberNickname?: string
  memberMobile?: string
  type: number
  typeName: string
  content: string
  contactMobile: string
  status: number
  statusName: string
  images?: string[] | null
  handleRemark?: string | null
  handlerId?: number | null
  handlerName?: string | null
  handleTime?: Date | null
  source: number
  sourceName: string
  createTime: Date
}

export const getCustomerServiceTicketPage = async (params: {
  pageNo: number
  pageSize: number
  type?: number
  status?: number
  source?: number
  keyword?: string
  createTime?: string[]
}) => {
  return await request.get<{ list: CustomerServiceTicketVO[]; total: number }>({
    url: '/trade/customer-service/ticket/page',
    params
  })
}

export const getCustomerServiceTicket = async (id: number) => {
  return await request.get<CustomerServiceTicketVO>({
    url: '/trade/customer-service/ticket/get',
    params: { id }
  })
}

export const handleCustomerServiceTicket = async (data: { id: number; handleRemark: string }) => {
  return await request.put({ url: '/trade/customer-service/ticket/handle', data })
}

// ==================== 在线维修 ====================

export interface CustomerServiceRepairVO {
  id: number
  memberId: number
  memberNickname?: string
  memberMobile?: string
  deviceModel: string
  faultDesc: string
  contactMobile: string
  receiverName: string
  receiverMobile: string
  receiverArea: string
  receiverDetail: string
  images?: string[] | null
  status: number
  statusName: string
  contactTime?: Date | null
  contactResult?: string | null
  shipCompany?: string | null
  shipNo?: string | null
  shipTime?: Date | null
  source: number
  sourceName: string
  creatorId?: number | null
  creatorName?: string | null
  createTime: Date
  // 维修订单（未开单时为 null）
  repairOrderId?: number | null
  repairFee?: number | null
  returnFreight?: number | null
  payableAmount?: number | null
  feeRemark?: string | null
  payStatus?: number | null
  payStatusName?: string | null
  refundStatus?: number | null
  refundStatusName?: string | null
}

export const getCustomerServiceRepairPage = async (params: {
  pageNo: number
  pageSize: number
  status?: number
  source?: number
  keyword?: string
  createTime?: string[]
}) => {
  return await request.get<{ list: CustomerServiceRepairVO[]; total: number }>({
    url: '/trade/customer-service/repair/page',
    params
  })
}

export const getCustomerServiceRepair = async (id: number) => {
  return await request.get<CustomerServiceRepairVO>({
    url: '/trade/customer-service/repair/get',
    params: { id }
  })
}

export const recordCustomerServiceRepairContact = async (data: { id: number; contactResult: string }) => {
  return await request.put({ url: '/trade/customer-service/repair/contact', data })
}

export const createCustomerServiceRepairOrder = async (data: {
  repairRequestId: number
  repairFee: number
  returnFreight: number
  feeRemark: string
}) => {
  return await request.post<{ repairOrderId: number; payableAmount: number; payStatus: number }>({
    url: '/trade/customer-service/repair/order/create',
    data
  })
}

export const updateCustomerServiceRepairStatus = async (data: {
  id: number
  status: number
  remark?: string
}) => {
  return await request.put({ url: '/trade/customer-service/repair/status', data })
}

export const cancelCustomerServiceRepair = async (data: { id: number; reason: string }) => {
  return await request.put({ url: '/trade/customer-service/repair/cancel', data })
}

export const confirmCustomerServiceRepairOffline = async (data: {
  id: number
  bankSerialNo?: string
  payerName?: string
  paidTime?: string
  remark?: string
}) => {
  return await request.post({ url: '/trade/customer-service/repair-order/confirm-offline', data })
}

// ==================== 维修订单（管理端） ====================

export interface CustomerServiceRepairOrderVO {
  id: number
  repairRequestId: number
  memberId: number
  memberNickname?: string | null
  memberMobile?: string | null
  deviceModel?: string | null
  repairFee: number
  returnFreight: number
  payableAmount: number
  feeRemark: string
  payStatus: number
  payStatusName: string
  payChannelCode?: string | null
  payOrderId?: number | null
  payTime?: Date | null
  offlineConfirmUserId?: number | null
  offlineConfirmTime?: Date | null
  refundStatus: number
  refundStatusName: string
  payRefundId?: number | null
  refundPrice?: number | null
  refundReason?: string | null
  refundTime?: Date | null
  refundOperatorId?: number | null
  completeTime?: Date | null
  completeOperatorId?: number | null
  completeOperatorName?: string | null
  repairStatus?: number | null
  repairStatusName?: string | null
  createTime: Date
}

export const getCustomerServiceRepairOrderPage = async (params: {
  pageNo: number
  pageSize: number
  id?: number
  memberId?: number
  payStatus?: number
  refundStatus?: number
  createTime?: string[]
}) => {
  return await request.get<{ list: CustomerServiceRepairOrderVO[]; total: number }>({
    url: '/trade/customer-service/repair-order/page',
    params
  })
}

export const refundCustomerServiceRepairOrder = async (data: { id: number; reason: string }) => {
  return await request.put({ url: '/trade/customer-service/repair-order/refund', data })
}

export const completeCustomerServiceRepairOrder = async (id: number) => {
  return await request.put({ url: '/trade/customer-service/repair-order/complete', params: { id } })
}

// ==================== 回收邮寄地址 ====================

export interface CustomerServiceAddressVO {
  receiverName: string
  receiverMobile: string
  areaName: string
  detailAddress: string
  shipRemark?: string | null
}

export const getCustomerServiceAddress = async () => {
  return await request.get<CustomerServiceAddressVO | null>({ url: '/trade/customer-service/address' })
}

export const updateCustomerServiceAddress = async (data: CustomerServiceAddressVO) => {
  return await request.put({ url: '/trade/customer-service/address', data })
}

// ==================== 代建（US75） ====================

export interface CustomerServiceMemberVO {
  id: number
  nickname?: string
  mobile?: string
}

export const searchCustomerServiceMembers = async (keyword: string) => {
  return await request.get<CustomerServiceMemberVO[]>({
    url: '/trade/customer-service/member/search',
    params: { keyword }
  })
}

export const createCustomerServiceTicket = async (data: {
  memberId: number
  type: number
  content: string
  contactMobile: string
  images?: string[]
}) => {
  return await request.post<number>({ url: '/trade/customer-service/ticket/create', data })
}

export const createCustomerServiceRepair = async (data: {
  memberId: number
  deviceModel: string
  faultDesc: string
  contactMobile: string
  receiverName: string
  receiverMobile: string
  receiverArea: string
  receiverDetail: string
  images?: string[]
}) => {
  return await request.post<number>({ url: '/trade/customer-service/repair/create', data })
}

