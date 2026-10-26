import request from '@/config/axios'

export interface OrgVO {
  id: number
  name: string
  ownerMemberId: number
  ownerNickname?: string
  ownerMobile?: string
  status: number
  memberCount?: number
  seatTotal?: number
  seatUsed?: number
  licenseEndDate?: string
  createTime?: string
}

export interface OrgSaveVO {
  id?: number
  name: string
  ownerMemberId: number
  remark?: string
}

export interface OrgMemberVO {
  memberId: number
  nickname?: string
  mobileMasked?: string
  role: number
  seatActive: boolean
  seatEndDate?: string
  joinedTime?: string
}

export interface OrgLicenseVO {
  id: number
  skuId: number
  periodMonths: number
  seatsTotal: number
  startDate: string
  endDate: string
  remainingDays: number
  sourceOrderNo: string
  refunded: boolean
  /** 已折算：剩余时长并入后续批次，本行只留痕 */
  folded: boolean
  active: boolean
}

export const getOrgPage = (params: {
  pageNo: number
  pageSize: number
  name?: string
  status?: number
}) => request.get<{ list: OrgVO[]; total: number }>({ url: '/member/org/page', params })

export const getOrg = (id: number) => request.get<OrgVO>({ url: '/member/org/get', params: { id } })

export const createOrg = (data: OrgSaveVO) => request.post<number>({ url: '/member/org/create', data })

export const updateOrg = (data: OrgSaveVO) => request.put<boolean>({ url: '/member/org/update', data })

export const updateOrgStatus = (id: number, status: number) =>
  request.put<boolean>({ url: '/member/org/update-status', data: { id, status } })

export const getOrgMemberPage = (params: {
  pageNo: number
  pageSize: number
  orgId: number
  keyword?: string
}) => request.get<{ list: OrgMemberVO[]; total: number }>({ url: '/member/org/member/page', params })

export const addOrgMember = (data: { orgId: number; mobile: string }) =>
  request.post<boolean>({ url: '/member/org/member/add', data })

export const removeOrgMember = (data: { orgId: number; memberId: number }) =>
  request.post<boolean>({ url: '/member/org/member/remove', data })

export const updateOrgMemberRole = (data: { orgId: number; memberId: number; role: number }) =>
  request.put<boolean>({ url: '/member/org/member/update-role', data })

export const transferOrgOwner = (data: { orgId: number; memberId: number }) =>
  request.post<boolean>({ url: '/member/org/owner/transfer', data })

export const assignOrgSeat = (data: { orgId: number; memberId: number }) =>
  request.post<boolean>({ url: '/member/org/seat/assign', data })

export const revokeOrgSeat = (data: { orgId: number; memberId: number }) =>
  request.post<boolean>({ url: '/member/org/seat/revoke', data })

export const getOrgLicensePage = (params: {
  pageNo: number
  pageSize: number
  orgId: number
}) => request.get<{ list: OrgLicenseVO[]; total: number }>({ url: '/member/org/license/page', params })

export const getOrgByMember = (memberId: number) =>
  request.get<{ orgId?: number; orgName?: string; role?: number; seatActive?: boolean; seatEndDate?: string }>({
    url: '/member/org/get-by-member',
    params: { memberId }
  })
