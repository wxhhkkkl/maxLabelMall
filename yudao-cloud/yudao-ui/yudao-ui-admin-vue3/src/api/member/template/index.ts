import request from '@/config/axios'

export interface LabelTemplateVO {
  id: number
  name: string
  sizeBytes: number
  widthMm: number
  heightMm: number
  category: string
  keywords: string
  shareStatus: number | null
  createTime: string
  updateTime: string
  content?: string
}

export interface SharedTemplateVO {
  id: number
  sourceTemplateId: number
  publisherMemberId: number
  publisherNickname?: string
  publisherMobile?: string
  name: string
  sizeBytes: number
  widthMm: number
  heightMm: number
  category: string
  keywords: string
  auditStatus: number
  rejectReason?: string
  auditUserId?: number
  auditTime?: string
  publishedTime?: string
  createTime: string
  content?: string
}

export const getTemplatePage = async (params: {
  userId: number
  pageNo: number
  pageSize: number
  keyword?: string
}) => {
  return await request.get<{ list: LabelTemplateVO[]; total: number }>({
    url: '/member/cloud-template/page',
    params
  })
}

export const getTemplateContent = async (id: number) => {
  return await request.get<LabelTemplateVO>({ url: '/member/cloud-template/get', params: { id } })
}

export const getSharedTemplatePage = async (params: {
  pageNo: number
  pageSize: number
  auditStatus?: number
  keyword?: string
}) => {
  return await request.get<{ list: SharedTemplateVO[]; total: number }>({
    url: '/member/shared-template/page',
    params
  })
}

export const getSharedTemplate = async (id: number) => {
  return await request.get<SharedTemplateVO>({ url: '/member/shared-template/get', params: { id } })
}

export const approveSharedTemplate = async (id: number) => {
  return await request.put({ url: '/member/shared-template/approve', params: { id } })
}

export const rejectSharedTemplate = async (id: number, reason: string) => {
  return await request.put({ url: '/member/shared-template/reject', data: { id, reason } })
}

export const offlineSharedTemplate = async (id: number) => {
  return await request.put({ url: '/member/shared-template/offline', params: { id } })
}
