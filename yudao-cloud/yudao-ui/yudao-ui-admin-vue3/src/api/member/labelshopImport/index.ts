import request from '@/config/axios'

export interface LabelshopPrecheckVO {
  orgId?: number
  orgName?: string | null
  ownerMemberId: number
  ownerNickname?: string
  ownerMobile?: string
  orgStatus: number
  licenseActive: boolean
  licenseEndDate?: string
  seatCount: number
  folderName?: string | null
  personalEdition?: string
  personalUsedBytes: number
  personalQuotaBytes: number
  enterpriseUsedBytes: number
  enterpriseQuotaBytes: number
  runningTaskId?: number | null
}

export interface LabelshopLibraryStatVO {
  library: number
  total: number
  success: number
  skip: number
  fail: number
}

export interface LabelshopTaskVO {
  id: number
  /** 导入对象：1 企业 2 个人会员 */
  subjectType?: number
  orgId?: number | null
  orgName?: string | null
  ownerMemberId: number
  folderId?: number | null
  folderName?: string | null
  sourceAccountMasked: string
  status: number
  phase?: string
  totalCount: number
  processedCount: number
  successCount: number
  skipCount: number
  failCount: number
  personalBytes: number
  enterpriseBytes: number
  personalUsedBytes: number
  personalQuotaBytes: number
  personalExceeded: boolean
  enterpriseUsedBytes: number
  enterpriseQuotaBytes: number
  enterpriseExceeded: boolean
  libraries: LabelshopLibraryStatVO[]
  uncleaned: boolean
  archiveJsonCount: number
  operatorId?: number | null
  operatorName?: string
  startedTime?: string
  finishedTime?: string
  errorMessage?: string | null
}

export interface LabelshopItemVO {
  id: number
  sourceLibrary: number
  sourceCategory?: string
  sourceName?: string
  targetScope: number
  format?: string
  editable?: boolean
  sizeBytes?: number
  status: number
  errorMessage?: string | null
}

export const getLabelshopPrecheck = (orgId: number) =>
  request.get<LabelshopPrecheckVO>({
    url: '/member/org/labelshop-import/precheck',
    params: { orgId }
  })

export const startLabelshopImport = (data: {
  orgId: number
  account: string
  password: string
  authorized: boolean
}) => request.post<{ taskId: number }>({ url: '/member/org/labelshop-import/start', data })

export const getLabelshopTask = (taskId: number) =>
  request.get<LabelshopTaskVO>({
    url: '/member/org/labelshop-import/task',
    params: { taskId }
  })

export const getLatestLabelshopTask = (orgId: number) =>
  request.get<LabelshopTaskVO | null>({
    url: '/member/org/labelshop-import/latest',
    params: { orgId }
  })

/** 个人会员代导：前置检查 */
export const getMemberLabelshopPrecheck = (memberId: number) =>
  request.get<LabelshopPrecheckVO>({
    url: '/member/user/labelshop-import/precheck',
    params: { memberId }
  })

/** 个人会员代导：发起导入（只导「我的模板库」到个人空间） */
export const startMemberLabelshopImport = (data: {
  memberId: number
  account: string
  password: string
  authorized: boolean
}) => request.post<{ taskId: number }>({ url: '/member/user/labelshop-import/start', data })

/** 个人会员代导：最近一次任务 */
export const getMemberLatestLabelshopTask = (memberId: number) =>
  request.get<LabelshopTaskVO | null>({
    url: '/member/user/labelshop-import/latest',
    params: { memberId }
  })

/** 个人会员代导：任务详情 */
export const getMemberLabelshopTask = (taskId: number) =>
  request.get<LabelshopTaskVO>({
    url: '/member/user/labelshop-import/task',
    params: { taskId }
  })

/** 个人会员代导：明细分页 */
export const getMemberLabelshopItems = (params: {
  taskId: number
  status?: number
  library?: number
  pageNo: number
  pageSize: number
}) =>
  request.get<{ list: LabelshopItemVO[]; total: number }>({
    url: '/member/user/labelshop-import/items',
    params
  })

export const getLabelshopItems = (params: {
  taskId: number
  status?: number
  library?: number
  pageNo: number
  pageSize: number
}) =>
  request.get<{ list: LabelshopItemVO[]; total: number }>({
    url: '/member/org/labelshop-import/items',
    params
  })
