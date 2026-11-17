import { get, post } from '@/config/http'

/**
 * 客户服务（021）
 *
 * 只走 `/app-api`，只返回当前登录会员的数据。
 */
export interface CustomerServiceTicket {
  id: number
  type: number // 1 投诉 2 咨询
  typeName: string
  content: string
  contactMobile: string
  status: number // 0 待处理 10 已处理
  statusName: string
  handleRemark?: string | null
  handleTime?: number | null
  images?: string[] | null
  createTime: number
}

export interface CustomerServiceTicketCreateReq {
  type: number
  content: string
  contactMobile: string
  images?: string[]
}

export function createCustomerServiceTicket(data: CustomerServiceTicketCreateReq): Promise<number> {
  return post<number>('/trade/customer-service/ticket/create', data)
}

export function getCustomerServiceTicketPage(params: {
  pageNo: number
  pageSize: number
}): Promise<{ list: CustomerServiceTicket[]; total: number }> {
  return get('/trade/customer-service/ticket/page', params)
}

export function getCustomerServiceTicket(id: number): Promise<CustomerServiceTicket> {
  return get(`/trade/customer-service/ticket/get`, { id })
}

// ==================== 在线维修 ====================

export interface CustomerServiceRepair {
  id: number
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
  contactTime?: number | null
  contactResult?: string | null
  shipCompany?: string | null
  shipNo?: string | null
  shipTime?: number | null
  createTime: number
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
  completeTime?: number | null
  returnAddress?: CustomerServiceReturnAddress | null
}

export interface CustomerServiceReturnAddress {
  receiverName: string
  receiverMobile: string
  areaName: string
  detailAddress: string
  shipRemark?: string | null
}

export interface CustomerServiceRepairCreateReq {
  deviceModel: string
  faultDesc: string
  contactMobile: string
  receiverName: string
  receiverMobile: string
  receiverArea: string
  receiverDetail: string
  images?: string[]
}

export function createCustomerServiceRepair(data: CustomerServiceRepairCreateReq): Promise<number> {
  return post<number>('/trade/customer-service/repair/create', data)
}

export function getCustomerServiceRepairPage(params: {
  pageNo: number
  pageSize: number
}): Promise<{ list: CustomerServiceRepair[]; total: number }> {
  return get('/trade/customer-service/repair/page', params)
}

export function getCustomerServiceRepair(id: number): Promise<CustomerServiceRepair> {
  return get('/trade/customer-service/repair/get', { id })
}

export interface CustomerServiceOfflinePayInfo {
  accountName?: string
  accountNo?: string
  bankName?: string
  tip?: string
}

export function payRepairOrderOnline(id: number): Promise<{ payOrderId: number }> {
  return post('/trade/customer-service/repair-order/pay-online', { id })
}

export function chooseRepairOrderOffline(
  id: number,
): Promise<{ payStatus: number; offline: CustomerServiceOfflinePayInfo }> {
  return post('/trade/customer-service/repair-order/pay-offline', { id })
}

export function markRepairShipped(data: {
  id: number
  shipCompany?: string
  shipNo: string
}): Promise<boolean> {
  return post('/trade/customer-service/repair/ship', data)
}
