import request from '@/config/axios'

/**
 * 授权看板（020）
 *
 * 契约见 `specs/020-license-dashboard/contracts/admin-api.md`
 * · 两个权限点分开授：`member:license:query`（看）与 `member:license:gift`（送）
 */
export interface LicenseSummaryVO {
  personalActiveCount: number
  personalSeatOnlyCount: number
  orgActiveCount: number
  orgSeatActiveCount: number
  orgSeatTotalCount: number
  yearNewCount: number
  yearRenewCount: number
  yearTotalCount: number
  // Q163：当年卡片按「个人会员数 + 企业席位数」拆分，合计由前端相加
  yearNewPersonalCount: number
  yearNewOrgSeatCount: number
  yearRenewPersonalCount: number
  yearRenewOrgSeatCount: number
  year: number
}

export interface LicenseCustomerVO {
  targetType: number // 1 个人 2 企业
  targetId: number
  targetName?: string
  targetMobile?: string
  orgName?: string
  sources?: string[] // ORDER / GRANT / ORG_SEAT
  startTime?: Date
  endTime?: Date
  remainingDays: number
  expiredDays: number
  active: boolean
  personalEndTime?: Date
  orgEndTime?: Date
  maxGiftMonths: number
}

/**
 * 赠送对象（Q162）：列出**全部**个人与企业，不限是否已有授权
 *
 * 无授权时 `endTime` 为 null、`maxGiftMonths = 12`，照常可以赠送。
 */
export interface LicenseGiftTargetVO {
  targetType: number // 1 个人 2 企业
  targetId: number
  targetName?: string
  targetMobile?: string
  status?: number // 0 启用 1 停用
  endTime?: Date
  remainingDays?: number
  active?: boolean
  maxGiftMonths: number
}

export interface LicenseGrantReqVO {
  targetType: number
  targetId: number
  contentType: number // 1 时长 2 席位
  months?: number
  seats?: number
  reason: string
}

export interface LicenseGrantVO {
  id: number
  targetType: number
  targetId: number
  targetName?: string
  contentType: number
  edition?: string
  months?: number
  seats?: number
  reason: string
  beforeEndTime?: Date
  afterEndTime?: Date
  beforeSeats?: number
  afterSeats?: number
  operatorId: number
  operatorName?: string
  createTime?: Date
  // 撤回（Q164）：非空 = 已撤回
  revokeTime?: Date
  revokeOperatorName?: string
  revokeReason?: string
}

export interface LicenseGrantRevokeReqVO {
  id: number
  reason: string
}

/** 总览 */
export const getLicenseSummary = async () => {
  return await request.get<LicenseSummaryVO>({ url: '/member/license/summary' })
}

/** 客户明细 */
export const getLicenseCustomerPage = async (params: {
  pageNo: number
  pageSize: number
  targetType?: number
  status?: number
  rightsType?: number
  keyword?: string
}) => {
  return await request.get<{ list: LicenseCustomerVO[]; total: number }>({
    url: '/member/license/customer/page',
    params
  })
}

/** 赠送对象（全部个人与企业，不限是否已有授权） */
export const getLicenseGiftTargetPage = async (params: {
  pageNo: number
  pageSize: number
  targetType?: number
  keyword?: string
}) => {
  return await request.get<{ list: LicenseGiftTargetVO[]; total: number }>({
    url: '/member/license/gift-target/page',
    params
  })
}

/** 赠送授权 */
export const grantLicense = async (data: LicenseGrantReqVO) => {
  return await request.post({ url: '/member/license/grant', data })
}

/** 撤回赠送（复用 member:license:gift 权限；只能撤该对象最近一笔） */
export const revokeLicenseGrant = async (data: LicenseGrantRevokeReqVO) => {
  return await request.post({ url: '/member/license/grant/revoke', data })
}

/** 赠送记录 */
export const getLicenseGrantPage = async (params: {
  pageNo: number
  pageSize: number
  targetType?: number
  targetId?: number
  operatorId?: number
  createTime?: Date[]
}) => {
  return await request.get<{ list: LicenseGrantVO[]; total: number }>({
    url: '/member/license/grant/page',
    params
  })
}
