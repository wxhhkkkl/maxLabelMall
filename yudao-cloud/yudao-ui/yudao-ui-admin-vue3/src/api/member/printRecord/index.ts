import request from '@/config/axios'

export interface PrintRecordVO {
  id: number
  memberId: number
  deviceId: string
  recordUid: string
  printTime: string
  templateTitle: string
  mode: string
  count: number
  copies: number
  physicalCount?: number
  sentCount?: number
  status: string
  printer: string
  createTime: string
}

export const getPrintRecordPage = async (params: {
  userId: number
  pageNo: number
  pageSize: number
  startTime?: string
  endTime?: string
}) => {
  return await request.get<{ list: PrintRecordVO[]; total: number }>({
    url: '/member/print-record/page',
    params
  })
}
